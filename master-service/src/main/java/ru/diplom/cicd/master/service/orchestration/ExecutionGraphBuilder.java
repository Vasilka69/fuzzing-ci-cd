package ru.diplom.cicd.master.service.orchestration;

import java.util.*;

import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.domain.entity.JobDependencyEntity;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;

@Component
public class ExecutionGraphBuilder {

    public ExecutionGraph build(List<StageEntity> stages, List<JobEntity> jobs, List<JobDependencyEntity> explicitDependencies) {
        Map<UUID, JobEntity> jobMap = jobs.stream().collect(HashMap::new, (m, j) -> m.put(j.getId(), j), HashMap::putAll);
        Map<UUID, List<ExecutionGraph.DependencyEdge>> edges = new HashMap<>();
        for (JobEntity job : jobs) {
            edges.put(job.getId(), new ArrayList<>());
        }

        Map<UUID, List<JobDependencyEntity>> explicitByJob = new HashMap<>();
        for (JobDependencyEntity dependency : explicitDependencies) {
            explicitByJob.computeIfAbsent(dependency.getJobId(), key -> new ArrayList<>()).add(dependency);
            edges.computeIfAbsent(dependency.getJobId(), key -> new ArrayList<>())
                    .add(new ExecutionGraph.DependencyEdge(dependency.getDependsOnJobId(), dependency.getCondition()));
        }

        Map<UUID, List<JobEntity>> jobsByStage = new HashMap<>();
        for (StageEntity stage : stages) {
            jobsByStage.put(stage.getId(), new ArrayList<>());
        }
        for (JobEntity job : jobs) {
            jobsByStage.computeIfAbsent(job.getStageId(), key -> new ArrayList<>()).add(job);
        }
        jobsByStage.values().forEach(list -> list.sort(Comparator.comparingInt(JobEntity::getPosition)));
        List<StageEntity> orderedStages = new ArrayList<>(stages);
        orderedStages.sort(Comparator.comparingInt(StageEntity::getPosition));

        for (int i = 0; i < orderedStages.size(); i++) {
            StageEntity stage = orderedStages.get(i);
            List<JobEntity> stageJobs = jobsByStage.getOrDefault(stage.getId(), List.of());
            for (int j = 0; j < stageJobs.size(); j++) {
                JobEntity job = stageJobs.get(j);
                boolean hasExplicit = explicitByJob.containsKey(job.getId());
                if (!hasExplicit && "sequential".equalsIgnoreCase(stage.getRunPolicy()) && j > 0) {
                    JobEntity prev = stageJobs.get(j - 1);
                    edges.get(job.getId()).add(new ExecutionGraph.DependencyEdge(prev.getId(), "on_success"));
                }
                if (!hasExplicit && i > 0) {
                    StageEntity previousStage = orderedStages.get(i - 1);
                    List<JobEntity> previousStageJobs = jobsByStage.getOrDefault(previousStage.getId(), List.of());
                    for (JobEntity previousStageJob : previousStageJobs) {
                        edges.get(job.getId()).add(new ExecutionGraph.DependencyEdge(previousStageJob.getId(), "on_success"));
                    }
                }
            }
        }

        return new ExecutionGraph(jobMap, edges);
    }
}
