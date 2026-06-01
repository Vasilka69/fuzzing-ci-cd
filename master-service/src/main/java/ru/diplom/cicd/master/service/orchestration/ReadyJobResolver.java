package ru.diplom.cicd.master.service.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;

@Component
public class ReadyJobResolver {

    public List<UUID> resolveReadyJobs(ExecutionGraph graph, Map<UUID, JobExecutionStatus> latestStatuses, Set<UUID> alreadyScheduledJobs) {
        List<UUID> ready = new ArrayList<>();
        for (UUID jobId : graph.jobs().keySet()) {
            if (alreadyScheduledJobs.contains(jobId)) {
                continue;
            }
            if (dependenciesSatisfied(graph.dependencies().getOrDefault(jobId, List.of()), latestStatuses)) {
                ready.add(jobId);
            }
        }
        return ready;
    }

    private boolean dependenciesSatisfied(List<ExecutionGraph.DependencyEdge> edges, Map<UUID, JobExecutionStatus> statuses) {
        for (ExecutionGraph.DependencyEdge edge : edges) {
            JobExecutionStatus upstream = statuses.get(edge.dependsOnJobId());
            if (upstream == null || !upstream.isFinalStatus()) {
                return false;
            }
            if (!conditionSatisfied(edge.condition(), upstream)) {
                return false;
            }
        }
        return true;
    }

    private boolean conditionSatisfied(String condition, JobExecutionStatus upstream) {
        if ("on_success".equalsIgnoreCase(condition)) {
            return upstream == JobExecutionStatus.SUCCESS;
        }
        if ("on_failure".equalsIgnoreCase(condition)) {
            return upstream == JobExecutionStatus.FAILED || upstream == JobExecutionStatus.TIMEOUT;
        }
        if ("always".equalsIgnoreCase(condition)) {
            return upstream.isFinalStatus();
        }
        return false;
    }
}
