package ru.diplom.cicd.master.service.orchestration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.exception.ApiException;

@Component
public class ExecutionGraphValidator {

    public void validate(ExecutionGraph graph) {
        for (UUID jobId : graph.jobs().keySet()) {
            if (hasCycle(jobId, graph, new HashSet<>(), new HashSet<>())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "graph_cycle_detected", "Pipeline graph has cyclic dependencies");
            }
        }
    }

    private boolean hasCycle(UUID current, ExecutionGraph graph, Set<UUID> visiting, Set<UUID> visited) {
        if (visited.contains(current)) {
            return false;
        }
        if (!visiting.add(current)) {
            return true;
        }
        List<ExecutionGraph.DependencyEdge> deps = graph.dependencies().getOrDefault(current, List.of());
        for (ExecutionGraph.DependencyEdge dep : deps) {
            if (hasCycle(dep.dependsOnJobId(), graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }
}
