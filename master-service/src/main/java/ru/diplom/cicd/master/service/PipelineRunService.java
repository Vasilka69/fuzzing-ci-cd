package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.api.dto.run.CreatePipelineRunRequest;
import ru.diplom.cicd.master.domain.entity.*;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;
import ru.diplom.cicd.master.domain.enums.JobType;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.domain.enums.PipelineRunStatus;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.CancellationRequestRepository;
import ru.diplom.cicd.master.repository.DeploymentApprovalRepository;
import ru.diplom.cicd.master.repository.DeploymentEnvironmentRepository;
import ru.diplom.cicd.master.repository.JobExecutionRepository;
import ru.diplom.cicd.master.repository.JobRepository;
import ru.diplom.cicd.master.repository.JobTemplateRepository;
import ru.diplom.cicd.master.repository.PipelineRepository;
import ru.diplom.cicd.master.repository.PipelineRunRepository;
import ru.diplom.cicd.master.service.messaging.JobMessageFactory;
import ru.diplom.cicd.master.service.messaging.KafkaTopicResolver;
import ru.diplom.cicd.master.service.messaging.contract.CancelCommand;
import ru.diplom.cicd.master.service.messaging.outbox.OutboxService;
import ru.diplom.cicd.master.service.orchestration.PipelineOrchestrator;
import ru.diplom.cicd.master.service.orchestration.RetryPolicyService;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@Service
@RequiredArgsConstructor
public class PipelineRunService {

    public record RunEventsPage(List<JobExecutionEntity> items, String nextCursor) {
    }

    private record EventCursor(OffsetDateTime updatedAt, UUID executionId) {
    }

    private final PipelineRepository pipelineRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobTemplateRepository jobTemplateRepository;
    private final DeploymentEnvironmentRepository deploymentEnvironmentRepository;
    private final DeploymentApprovalRepository deploymentApprovalRepository;
    private final CancellationRequestRepository cancellationRequestRepository;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final JobMessageFactory jobMessageFactory;
    private final KafkaTopicResolver kafkaTopicResolver;
    private final OutboxService outboxService;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final AuditService auditService;
    private final RetryPolicyService retryPolicyService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @Transactional
    public PipelineRunEntity createRun(UUID pipelineId, CreatePipelineRunRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        return createRunInternal(pipelineId, request, userId, true);
    }

    @Transactional
    public PipelineRunEntity createRunBySystem(UUID pipelineId, String triggerType, JsonNode payload) {
        CreatePipelineRunRequest request = new CreatePipelineRunRequest(triggerType, payload);
        return createRunInternal(pipelineId, request, null, false);
    }

    @Transactional(readOnly = true)
    public List<PipelineRunEntity> listRuns(UUID pipelineId, String status) {
        UUID userId = userContextService.currentUserIdOrNull();
        if (pipelineId != null) {
            permissionService.require(userId, Permission.VIEW, "pipeline", pipelineId);
            List<PipelineRunEntity> runs = pipelineRunRepository.findByPipelineIdOrderByStartedAtDesc(pipelineId);
            if (status == null || status.isBlank()) {
                return runs;
            }
            return runs.stream().filter(run -> status.equalsIgnoreCase(run.getStatus())).toList();
        }
        permissionService.require(userId, Permission.VIEW, "system", null);
        return pipelineRunRepository.findAll().stream()
                .filter(run -> status == null || status.isBlank() || status.equalsIgnoreCase(run.getStatus()))
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineRunEntity getRun(UUID runId) {
        PipelineRunEntity run = pipelineRunRepository.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.VIEW, "pipeline", run.getPipelineId());
        return run;
    }

    @Transactional
    public PipelineRunEntity cancelRun(UUID runId) {
        PipelineRunEntity run = getRun(runId);
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.CANCEL, "pipeline", run.getPipelineId());

        run.setStatus(PipelineRunStatus.CANCELING.value());
        pipelineRunRepository.save(run);

        List<JobExecutionEntity> active = jobExecutionRepository.findByPipelineRunIdAndStatusIn(
                runId, List.of("queued", "running", "waiting_approval", "canceling"));
        for (JobExecutionEntity execution : active) {
            execution.setStatus("canceling");
            execution.setUpdatedAt(OffsetDateTime.now());
            jobExecutionRepository.save(execution);

            CancellationRequestEntity cancel = CancellationRequestEntity.builder()
                    .id(UUID.randomUUID())
                    .pipelineRunId(runId)
                    .jobExecutionId(execution.getId())
                    .requestedBy(userId)
                    .reason("user_requested")
                    .status("requested")
                    .gracePeriodSeconds(30)
                    .requestedAt(OffsetDateTime.now())
                    .build();
            cancellationRequestRepository.save(cancel);

            CancelCommand command = new CancelCommand(
                    1,
                    UUID.randomUUID(),
                    run.getCorrelationId(),
                    run.getId(),
                    execution.getId(),
                    "user_requested",
                    userId == null ? "unknown" : userId.toString(),
                    30,
                    OffsetDateTime.now()
            );
            outboxService.enqueue(
                    "job_execution",
                    execution.getId(),
                    "job.cancel",
                    kafkaTopicResolver.resolveCancelTopic(),
                    execution.getId().toString(),
                    command
            );
        }

        auditService.record(userId, "PIPELINE_RUN_CANCEL", "pipeline_run", runId, null);
        return run;
    }

    @Transactional(readOnly = true)
    public List<JobExecutionEntity> listExecutions(UUID runId) {
        PipelineRunEntity run = getRun(runId);
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.VIEW, "pipeline", run.getPipelineId());
        return jobExecutionRepository.findByPipelineRunId(runId);
    }

    @Transactional(readOnly = true)
    public RunEventsPage listRunEvents(UUID runId, String cursor, Integer limit) {
        PipelineRunEntity run = getRun(runId);
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.VIEW, "pipeline", run.getPipelineId());

        EventCursor eventCursor = parseEventCursor(cursor);
        int pageSize = normalizePageSize(limit);
        List<JobExecutionEntity> sorted = jobExecutionRepository.findByPipelineRunId(runId).stream()
                .sorted(
                        Comparator.comparing(this::sortTimestamp, Comparator.reverseOrder())
                                .thenComparing(JobExecutionEntity::getId, Comparator.reverseOrder())
                )
                .filter(execution -> isOlderThanCursor(execution, eventCursor))
                .toList();

        int toIndex = Math.min(pageSize + 1, sorted.size());
        List<JobExecutionEntity> window = sorted.subList(0, toIndex);
        boolean hasMore = window.size() > pageSize;
        List<JobExecutionEntity> items = hasMore ? window.subList(0, pageSize) : window;
        String nextCursor = hasMore && !items.isEmpty()
                ? encodeEventCursor(items.getLast())
                : null;
        return new RunEventsPage(items, nextCursor);
    }

    @Transactional
    public List<JobExecutionEntity> dispatchReadyJobs(PipelineRunEntity run, List<JobExecutionEntity> existingExecutions) {
        List<JobExecutionEntity> mutableExecutions = new ArrayList<>(existingExecutions);
        List<JobEntity> readyJobs = pipelineOrchestrator.resolveReadyJobs(run.getPipelineId(), mutableExecutions);
        List<JobExecutionEntity> created = new ArrayList<>();
        for (JobEntity readyJob : readyJobs) {
            int attempt = mutableExecutions.stream()
                    .filter(execution -> execution.getJobId().equals(readyJob.getId()))
                    .map(JobExecutionEntity::getAttempt)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
            JobExecutionEntity execution = createExecution(run, readyJob, attempt);
            mutableExecutions.add(execution);
            created.add(execution);
        }
        return created;
    }

    @Transactional
    public PipelineRunEntity retryRun(UUID runId) {
        PipelineRunEntity run = getRun(runId);
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.RUN, "pipeline", run.getPipelineId());

        List<JobExecutionEntity> executions = jobExecutionRepository.findByPipelineRunId(runId);
        Map<UUID, JobExecutionEntity> latestByJob = new LinkedHashMap<>();
        executions.stream()
                .sorted(Comparator.comparing(JobExecutionEntity::getAttempt).reversed())
                .forEach(execution -> latestByJob.putIfAbsent(execution.getJobId(), execution));

        List<JobExecutionEntity> retried = new ArrayList<>();
        for (JobExecutionEntity latest : latestByJob.values()) {
            JobEntity job = jobRepository.findById(latest.getJobId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Job not found"));
            if (!isRetryCandidate(latest, job)) {
                continue;
            }
            latest.setStatus(JobExecutionStatus.RETRYING.value());
            latest.setUpdatedAt(OffsetDateTime.now());
            jobExecutionRepository.save(latest);
            retried.add(createExecution(run, job, latest.getAttempt() + 1));
        }

        if (retried.isEmpty()) {
            return run;
        }
        run.setStatus(retried.stream().anyMatch(ex -> JobExecutionStatus.WAITING_APPROVAL.value().equals(ex.getStatus()))
                ? PipelineRunStatus.WAITING_APPROVAL.value()
                : PipelineRunStatus.RUNNING.value());
        run.setFinishedAt(null);
        pipelineRunRepository.save(run);
        auditService.record(userId, "PIPELINE_RUN_RETRY", "pipeline_run", runId, Map.of("retriedExecutions", retried.size()));
        return run;
    }

    @Transactional
    public JobExecutionEntity retryExecution(UUID executionId) {
        JobExecutionEntity latest = jobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "execution_not_found", "Job execution not found"));
        PipelineRunEntity run = getRun(latest.getPipelineRunId());
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.RUN, "pipeline", run.getPipelineId());

        JobEntity job = jobRepository.findById(latest.getJobId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Job not found"));
        if (!isRetryCandidate(latest, job)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "retry_not_allowed", "Execution status is not retryable");
        }
        int nextAttempt = jobExecutionRepository.findByPipelineRunIdAndJobIdOrderByAttemptDesc(run.getId(), job.getId()).stream()
                .map(JobExecutionEntity::getAttempt)
                .max(Integer::compareTo)
                .orElse(latest.getAttempt()) + 1;

        latest.setStatus(JobExecutionStatus.RETRYING.value());
        latest.setUpdatedAt(OffsetDateTime.now());
        jobExecutionRepository.save(latest);
        JobExecutionEntity retried = createExecution(run, job, nextAttempt);
        run.setStatus(JobExecutionStatus.WAITING_APPROVAL.value().equals(retried.getStatus())
                ? PipelineRunStatus.WAITING_APPROVAL.value()
                : PipelineRunStatus.RUNNING.value());
        run.setFinishedAt(null);
        pipelineRunRepository.save(run);
        auditService.record(userId, "JOB_EXECUTION_RETRY", "job_execution", executionId, Map.of("newExecutionId", retried.getId()));
        return retried;
    }

    @Transactional
    public void enqueueApprovedExecution(UUID approvalId) {
        DeploymentApprovalEntity approval = deploymentApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "approval_not_found", "Approval not found"));
        if (!"approved".equalsIgnoreCase(approval.getStatus())) {
            return;
        }
        JobExecutionEntity execution = jobExecutionRepository.findById(approval.getJobExecutionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "execution_not_found", "Job execution not found"));
        if (!JobExecutionStatus.WAITING_APPROVAL.value().equals(execution.getStatus())) {
            return;
        }
        PipelineRunEntity run = pipelineRunRepository.findById(execution.getPipelineRunId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        JobEntity job = jobRepository.findById(execution.getJobId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Job not found"));

        execution.setStatus(JobExecutionStatus.QUEUED.value());
        execution.setUpdatedAt(OffsetDateTime.now());
        jobExecutionRepository.save(execution);
        enqueueExecution(run, job, execution);

        run.setStatus(PipelineRunStatus.RUNNING.value());
        run.setFinishedAt(null);
        pipelineRunRepository.save(run);
    }

    @Transactional
    public void rejectApprovalExecution(UUID approvalId) {
        DeploymentApprovalEntity approval = deploymentApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "approval_not_found", "Approval not found"));
        JobExecutionEntity execution = jobExecutionRepository.findById(approval.getJobExecutionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "execution_not_found", "Job execution not found"));
        if (!JobExecutionStatus.WAITING_APPROVAL.value().equals(execution.getStatus())) {
            return;
        }
        execution.setStatus(JobExecutionStatus.SKIPPED.value());
        execution.setFinishedAt(OffsetDateTime.now());
        execution.setUpdatedAt(OffsetDateTime.now());
        jobExecutionRepository.save(execution);
        recomputeRunStatus(execution.getPipelineRunId());
    }

    @Transactional
    public void recomputeRunStatus(UUID runId) {
        PipelineRunEntity run = pipelineRunRepository.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        List<JobExecutionEntity> executions = jobExecutionRepository.findByPipelineRunId(runId);
        PipelineRunStatus status = pipelineOrchestrator.reduceRunStatus(executions);
        run.setStatus(status.value());
        if (status.isFinalStatus()) {
            run.setFinishedAt(OffsetDateTime.now());
        }
        pipelineRunRepository.save(run);
    }

    private JobExecutionEntity createExecution(PipelineRunEntity run, JobEntity job, int attempt) {
        JobExecutionEntity execution = JobExecutionEntity.builder()
                .id(UUID.randomUUID())
                .pipelineRunId(run.getId())
                .jobId(job.getId())
                .attempt(attempt)
                .status(JobExecutionStatus.QUEUED.value())
                .result(objectMapper.createObjectNode())
                .metrics(objectMapper.createObjectNode())
                .artifactManifest(objectMapper.createArrayNode())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        execution = jobExecutionRepository.save(execution);

        DeploymentEnvironmentEntity approvalEnvironment = resolveApprovalEnvironment(job);
        if (approvalEnvironment != null) {
            execution.setStatus(JobExecutionStatus.WAITING_APPROVAL.value());
            execution.setUpdatedAt(OffsetDateTime.now());
            execution = jobExecutionRepository.save(execution);
            createDeploymentApproval(run, execution, approvalEnvironment.getId());
            return execution;
        }

        enqueueExecution(run, job, execution);
        return execution;
    }

    private void enqueueExecution(PipelineRunEntity run, JobEntity job, JobExecutionEntity execution) {
        String templatePath = job.getJobTemplateId() == null
                ? job.getJobType() + "/custom"
                : jobTemplateRepository.findById(job.getJobTemplateId())
                        .map(JobTemplateEntity::getPath)
                        .orElse(job.getJobType() + "/custom");
        var message = jobMessageFactory.build(
                run,
                job,
                execution,
                run.getPipelineId(),
                job.getStageId(),
                templatePath
        );
        outboxService.enqueue(
                "job_execution",
                execution.getId(),
                "job.dispatch",
                kafkaTopicResolver.resolveJobTopic(JobType.fromValue(job.getJobType())),
                execution.getId().toString(),
                message
        );
    }

    private DeploymentEnvironmentEntity resolveApprovalEnvironment(JobEntity job) {
        if (!JobType.DEPLOY.value().equalsIgnoreCase(job.getJobType())) {
            return null;
        }
        String envName = readEnvironmentName(job.getParams());
        if (envName == null || envName.isBlank()) {
            return null;
        }
        DeploymentEnvironmentEntity environment = deploymentEnvironmentRepository.findByName(envName).orElse(null);
        if (environment == null || environment.getConfig() == null) {
            return null;
        }
        boolean isProtected = environment.getConfig().path("protected").asBoolean(false)
                || environment.getConfig().path("requires_approval").asBoolean(false);
        return isProtected ? environment : null;
    }

    private void createDeploymentApproval(PipelineRunEntity run, JobExecutionEntity execution, UUID environmentId) {
        DeploymentApprovalEntity approval = DeploymentApprovalEntity.builder()
                .id(UUID.randomUUID())
                .pipelineRunId(run.getId())
                .jobExecutionId(execution.getId())
                .environmentId(environmentId)
                .requestedBy(run.getStartedBy())
                .status("pending")
                .createdAt(OffsetDateTime.now())
                .build();
        deploymentApprovalRepository.save(approval);
    }

    private boolean isRetryCandidate(JobExecutionEntity execution, JobEntity job) {
        if (execution == null || job == null) {
            return false;
        }
        JobExecutionStatus status;
        try {
            status = JobExecutionStatus.fromValue(execution.getStatus());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!(status == JobExecutionStatus.FAILED
                || status == JobExecutionStatus.TIMEOUT
                || status == JobExecutionStatus.CANCELED
                || status == JobExecutionStatus.SKIPPED)) {
            return false;
        }
        if (execution.getAttempt() == null || job.getMaxAttempts() == null || execution.getAttempt() >= job.getMaxAttempts()) {
            return false;
        }
        if (status == JobExecutionStatus.FAILED || status == JobExecutionStatus.TIMEOUT) {
            return retryPolicyService.canRetry(execution, job);
        }
        return true;
    }

    private String readEnvironmentName(JsonNode params) {
        if (params == null) {
            return null;
        }
        JsonNode node = params.get("environment");
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private PipelineRunEntity createRunInternal(
            UUID pipelineId,
            CreatePipelineRunRequest request,
            UUID startedBy,
            boolean enforcePermission
    ) {
        if (enforcePermission) {
            permissionService.require(startedBy, Permission.RUN, "pipeline", pipelineId);
        }
        PipelineEntity pipeline = pipelineRepository.findByIdAndIsActive(pipelineId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pipeline_not_found", "Pipeline not found or inactive"));

        ObjectNode payload = request == null || request.triggerPayload() == null
                ? objectMapper.createObjectNode()
                : toObjectNode(sensitiveDataSanitizer.redact(request.triggerPayload()));
        PipelineRunEntity run = PipelineRunEntity.builder()
                .id(UUID.randomUUID())
                .pipelineId(pipeline.getId())
                .status(PipelineRunStatus.QUEUED.value())
                .correlationId(UUID.randomUUID())
                .startedBy(startedBy)
                .triggeredByType(request == null || request.triggerType() == null ? "user" : request.triggerType())
                .triggerPayload(payload)
                .startedAt(OffsetDateTime.now())
                .build();
        run = pipelineRunRepository.save(run);

        List<JobExecutionEntity> created = dispatchReadyJobs(run, List.of());
        if (created.isEmpty()) {
            run.setStatus(PipelineRunStatus.SUCCESS.value());
            run.setFinishedAt(OffsetDateTime.now());
        } else if (created.stream().anyMatch(execution -> JobExecutionStatus.WAITING_APPROVAL.value().equals(execution.getStatus()))) {
            run.setStatus(PipelineRunStatus.WAITING_APPROVAL.value());
        } else {
            run.setStatus(PipelineRunStatus.RUNNING.value());
        }
        run = pipelineRunRepository.save(run);
        auditService.record(startedBy, "PIPELINE_RUN_CREATE", "pipeline_run", run.getId(), run);
        return run;
    }

    private int normalizePageSize(Integer limit) {
        if (limit == null || limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private boolean isOlderThanCursor(JobExecutionEntity execution, EventCursor cursor) {
        if (cursor == null) {
            return true;
        }
        OffsetDateTime timestamp = sortTimestamp(execution);
        if (timestamp.isBefore(cursor.updatedAt())) {
            return true;
        }
        if (timestamp.isAfter(cursor.updatedAt())) {
            return false;
        }
        return execution.getId().compareTo(cursor.executionId()) < 0;
    }

    private EventCursor parseEventCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split("\\|");
        if (parts.length != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Cursor has invalid format");
        }
        try {
            long epochMillis = Long.parseLong(parts[0]);
            UUID executionId = UUID.fromString(parts[1]);
            return new EventCursor(OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneOffset.UTC), executionId);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Cursor has invalid format");
        }
    }

    private String encodeEventCursor(JobExecutionEntity execution) {
        long epochMillis = sortTimestamp(execution).toInstant().toEpochMilli();
        return epochMillis + "|" + execution.getId();
    }

    private OffsetDateTime sortTimestamp(JobExecutionEntity execution) {
        if (execution.getUpdatedAt() != null) {
            return execution.getUpdatedAt();
        }
        if (execution.getCreatedAt() != null) {
            return execution.getCreatedAt();
        }
        return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC);
    }

    private ObjectNode toObjectNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("value", node);
        return wrapper;
    }
}
