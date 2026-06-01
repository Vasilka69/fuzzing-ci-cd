package ru.diplom.cicd.master.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.CancellationRequestEntity;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.CancellationRequestRepository;
import ru.diplom.cicd.master.repository.JobExecutionRepository;
import ru.diplom.cicd.master.repository.PipelineRunRepository;
import ru.diplom.cicd.master.service.messaging.KafkaTopicResolver;
import ru.diplom.cicd.master.service.messaging.contract.CancelCommand;
import ru.diplom.cicd.master.service.messaging.outbox.OutboxService;

@Service
@RequiredArgsConstructor
public class JobExecutionService {

    private final JobExecutionRepository jobExecutionRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final CancellationRequestRepository cancellationRequestRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final OutboxService outboxService;
    private final KafkaTopicResolver kafkaTopicResolver;
    private final AuditService auditService;
    private final PipelineRunService pipelineRunService;

    @Transactional(readOnly = true)
    public List<JobExecutionEntity> list(UUID pipelineRunId) {
        PipelineRunEntity run = pipelineRunRepository.findById(pipelineRunId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "pipeline", run.getPipelineId());
        return jobExecutionRepository.findByPipelineRunId(pipelineRunId);
    }

    @Transactional(readOnly = true)
    public JobExecutionEntity get(UUID executionId) {
        JobExecutionEntity execution = jobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "execution_not_found", "Job execution not found"));
        PipelineRunEntity run = pipelineRunRepository.findById(execution.getPipelineRunId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "pipeline", run.getPipelineId());
        return execution;
    }

    @Transactional
    public JobExecutionEntity cancel(UUID executionId) {
        JobExecutionEntity execution = get(executionId);
        PipelineRunEntity run = pipelineRunRepository.findById(execution.getPipelineRunId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "run_not_found", "Pipeline run not found"));
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.CANCEL, "pipeline", run.getPipelineId());

        if ("success".equals(execution.getStatus())
                || "failed".equals(execution.getStatus())
                || "timeout".equals(execution.getStatus())
                || "canceled".equals(execution.getStatus())
                || "skipped".equals(execution.getStatus())) {
            return execution;
        }

        execution.setStatus("canceling");
        execution.setUpdatedAt(OffsetDateTime.now());
        execution = jobExecutionRepository.save(execution);

        CancellationRequestEntity cancel = CancellationRequestEntity.builder()
                .id(UUID.randomUUID())
                .pipelineRunId(run.getId())
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
        auditService.record(userId, "JOB_EXECUTION_CANCEL", "job_execution", executionId, null);
        return execution;
    }

    @Transactional
    public JobExecutionEntity retry(UUID executionId) {
        return pipelineRunService.retryExecution(executionId);
    }
}
