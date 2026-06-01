package ru.diplom.cicd.master.service.orchestration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.diplom.cicd.master.domain.entity.JobEntity;

public record ExecutionGraph(
        Map<UUID, JobEntity> jobs,
        Map<UUID, List<DependencyEdge>> dependencies
) {
    public record DependencyEdge(UUID dependsOnJobId, String condition) {}
}
