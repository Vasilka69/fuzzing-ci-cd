package ru.diplom.cicd.master.service.orchestration;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import ru.diplom.cicd.master.domain.entity.JobDependencyEntity;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;
import ru.diplom.cicd.master.domain.enums.PipelineRunStatus;
import ru.diplom.cicd.master.repository.JobDependencyRepository;
import ru.diplom.cicd.master.repository.JobRepository;
import ru.diplom.cicd.master.repository.StageRepository;

@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final StageRepository stageRepository;
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final ExecutionGraphBuilder executionGraphBuilder;
    private final ExecutionGraphValidator executionGraphValidator;
    private final ReadyJobResolver readyJobResolver;
    private final PipelineStateReducer pipelineStateReducer;

    public List<JobEntity> resolveReadyJobs(UUID pipelineId, List<JobExecutionEntity> executions) {
        List<StageEntity> stages = stageRepository.findByPipelineIdOrderByPositionAsc(pipelineId);
        List<UUID> stageIds = stages.stream().map(StageEntity::getId).toList();
        List<JobEntity> jobs = stageIds.isEmpty() ? List.of() : jobRepository.findByStageIdInOrderByPositionAsc(stageIds);
        List<UUID> jobIds = jobs.stream().map(JobEntity::getId).toList();
        List<JobDependencyEntity> deps = jobIds.isEmpty() ? List.of() : jobDependencyRepository.findByJobIdIn(jobIds);

        ExecutionGraph graph = executionGraphBuilder.build(stages, jobs, deps);
        executionGraphValidator.validate(graph);

        Map<UUID, JobExecutionStatus> latestStatuses = executions.stream()
                .sorted((a, b) -> Integer.compare(b.getAttempt(), a.getAttempt()))
                .collect(java.util.stream.Collectors.toMap(
                        JobExecutionEntity::getJobId,
                        e -> JobExecutionStatus.fromValue(e.getStatus()),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
        Set<UUID> alreadyScheduled = executions.stream().map(JobExecutionEntity::getJobId).collect(java.util.stream.Collectors.toSet());
        List<UUID> readyJobIds = readyJobResolver.resolveReadyJobs(graph, latestStatuses, alreadyScheduled);
        return readyJobIds.stream()
                .map(graph.jobs()::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(JobEntity::getPosition))
                .toList();
    }

    public PipelineRunStatus reduceRunStatus(List<JobExecutionEntity> executions) {
        List<JobExecutionStatus> statuses = executions.stream()
                .map(execution -> JobExecutionStatus.fromValue(execution.getStatus()))
                .toList();
        return pipelineStateReducer.reduce(statuses);
    }
}
