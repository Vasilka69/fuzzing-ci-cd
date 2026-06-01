package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.api.dto.pipeline.CreateDependencyRequest;
import ru.diplom.cicd.master.api.dto.pipeline.CreateJobRequest;
import ru.diplom.cicd.master.api.dto.pipeline.CreatePipelineRequest;
import ru.diplom.cicd.master.api.dto.pipeline.CreateStageRequest;
import ru.diplom.cicd.master.api.dto.pipeline.PipelineDetailsResponse;
import ru.diplom.cicd.master.api.dto.pipeline.UpdatePipelineRequest;
import ru.diplom.cicd.master.api.mapper.PipelineMapper;
import ru.diplom.cicd.master.domain.entity.JobDependencyEntity;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.JobParamsEntity;
import ru.diplom.cicd.master.domain.entity.PipelineEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.JobDependencyRepository;
import ru.diplom.cicd.master.repository.JobParamsRepository;
import ru.diplom.cicd.master.repository.JobRepository;
import ru.diplom.cicd.master.repository.PipelineRepository;
import ru.diplom.cicd.master.repository.StageRepository;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final StageRepository stageRepository;
    private final JobRepository jobRepository;
    private final JobParamsRepository jobParamsRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final PipelineMapper pipelineMapper;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @Transactional(readOnly = true)
    public List<PipelineEntity> list(UUID folderId, Boolean isActive, String query) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.VIEW, "system", null);

        List<PipelineEntity> items;
        if (folderId != null && isActive != null) {
            items = pipelineRepository.findByFolderIdAndIsActive(folderId, isActive);
        } else if (isActive != null) {
            items = pipelineRepository.findByIsActive(isActive);
        } else {
            items = pipelineRepository.findAll();
        }

        if (query == null || query.isBlank()) {
            return items;
        }
        String needle = query.toLowerCase();
        return items.stream()
                .filter(p -> p.getName().toLowerCase().contains(needle)
                        || (p.getDescription() != null && p.getDescription().toLowerCase().contains(needle)))
                .toList();
    }

    @Transactional
    public PipelineEntity create(CreatePipelineRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "system", null);

        PipelineEntity pipeline = PipelineEntity.builder()
                .id(UUID.randomUUID())
                .folderId(request.folderId())
                .name(request.name())
                .description(request.description())
                .isActive(true)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        PipelineEntity saved = pipelineRepository.save(pipeline);
        auditService.record(userId, "PIPELINE_CREATE", "pipeline", saved.getId(), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PipelineEntity get(UUID id) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.VIEW, "pipeline", id);
        return pipelineRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pipeline_not_found", "Pipeline not found"));
    }

    @Transactional
    public PipelineEntity update(UUID id, UpdatePipelineRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "pipeline", id);

        PipelineEntity pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pipeline_not_found", "Pipeline not found"));
        if (request.name() != null && !request.name().isBlank()) {
            pipeline.setName(request.name());
        }
        if (request.description() != null) {
            pipeline.setDescription(request.description());
        }
        if (request.isActive() != null) {
            pipeline.setIsActive(request.isActive());
        }
        pipeline.setUpdatedAt(OffsetDateTime.now());
        PipelineEntity saved = pipelineRepository.save(pipeline);
        auditService.record(userId, "PIPELINE_UPDATE", "pipeline", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "pipeline", id);
        PipelineEntity pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pipeline_not_found", "Pipeline not found"));
        pipeline.setIsActive(false);
        pipeline.setUpdatedAt(OffsetDateTime.now());
        pipelineRepository.save(pipeline);
        auditService.record(userId, "PIPELINE_DEACTIVATE", "pipeline", id, null);
    }

    @Transactional
    public StageEntity createStage(UUID pipelineId, CreateStageRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "pipeline", pipelineId);
        if (!pipelineRepository.existsById(pipelineId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "pipeline_not_found", "Pipeline not found");
        }
        StageEntity stage = StageEntity.builder()
                .id(UUID.randomUUID())
                .pipelineId(pipelineId)
                .position(request.position())
                .name(request.name())
                .description(request.description())
                .runPolicy(request.runPolicy() == null ? "sequential" : request.runPolicy())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        StageEntity saved = stageRepository.save(stage);
        auditService.record(userId, "STAGE_CREATE", "stage", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public JobEntity createJob(UUID stageId, CreateJobRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        StageEntity stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "stage_not_found", "Stage not found"));
        permissionService.require(userId, Permission.EDIT, "pipeline", stage.getPipelineId());

        ObjectNode empty = objectMapper.createObjectNode();
        JsonNode params = request.params() == null ? empty : request.params();
        sensitiveDataSanitizer.requireNoInlineSecrets(params, "params");

        JobEntity job = JobEntity.builder()
                .id(UUID.randomUUID())
                .stageId(stageId)
                .jobTemplateId(request.jobTemplateId())
                .position(request.position())
                .name(request.name())
                .jobType(request.jobType())
                .params(params)
                .script(request.script())
                .isScriptPrimary(Boolean.TRUE.equals(request.isScriptPrimary()))
                .condition(request.condition() == null ? "on_success" : request.condition())
                .timeoutSeconds(request.timeoutSeconds() == null ? 3600 : request.timeoutSeconds())
                .maxAttempts(request.maxAttempts() == null ? 1 : request.maxAttempts())
                .resourceLimits(request.resourceLimits() == null ? empty : request.resourceLimits())
                .sandboxPolicy(request.sandboxPolicy() == null ? empty : request.sandboxPolicy())
                .continueOnError(Boolean.TRUE.equals(request.continueOnError()))
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        JobEntity saved = jobRepository.save(job);
        persistJobParamsSnapshot(saved);
        auditService.record(userId, "JOB_CREATE", "job", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public JobDependencyEntity addDependency(UUID jobId, CreateDependencyRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Job not found"));
        StageEntity stage = stageRepository.findById(job.getStageId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "stage_not_found", "Stage not found"));
        permissionService.require(userId, Permission.EDIT, "pipeline", stage.getPipelineId());

        if (!jobRepository.existsById(request.dependsOnJobId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "dependency_not_found", "Depends-on job does not exist");
        }
        JobDependencyEntity dependency = JobDependencyEntity.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .dependsOnJobId(request.dependsOnJobId())
                .condition(request.condition() == null ? "on_success" : request.condition())
                .createdAt(OffsetDateTime.now())
                .build();
        JobDependencyEntity saved = jobDependencyRepository.save(dependency);
        auditService.record(userId, "JOB_DEPENDENCY_CREATE", "job_dependency", saved.getId(), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PipelineDetailsResponse getDetails(UUID pipelineId) {
        PipelineEntity pipeline = get(pipelineId);
        List<StageEntity> stages = stageRepository.findByPipelineIdOrderByPositionAsc(pipelineId);
        List<UUID> stageIds = stages.stream().map(StageEntity::getId).toList();
        List<JobEntity> jobs = stageIds.isEmpty()
                ? List.of()
                : jobRepository.findByStageIdInOrderByPositionAsc(stageIds);
        List<UUID> jobIds = jobs.stream().map(JobEntity::getId).toList();
        List<JobDependencyEntity> dependencies = jobIds.isEmpty()
                ? List.of()
                : jobDependencyRepository.findByJobIdIn(jobIds);

        jobs = jobs.stream()
                .sorted(Comparator.comparing(JobEntity::getStageId).thenComparing(JobEntity::getPosition))
                .toList();

        return new PipelineDetailsResponse(
                pipelineMapper.toPipeline(pipeline),
                stages.stream().map(pipelineMapper::toStage).toList(),
                jobs.stream().map(pipelineMapper::toJob).toList(),
                dependencies.stream().map(pipelineMapper::toDependency).toList()
        );
    }

    private void persistJobParamsSnapshot(JobEntity job) {
        if (job == null) {
            return;
        }
        JobParamsEntity paramsSnapshot = JobParamsEntity.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .jobTemplateId(job.getJobTemplateId())
                .params(job.getParams() == null ? objectMapper.createObjectNode() : job.getParams())
                .createdAt(OffsetDateTime.now())
                .build();
        jobParamsRepository.save(paramsSnapshot);
    }
}
