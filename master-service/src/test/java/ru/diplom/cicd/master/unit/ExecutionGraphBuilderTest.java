package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;
import ru.diplom.cicd.master.service.orchestration.ExecutionGraphBuilder;

class ExecutionGraphBuilderTest {

    private final ExecutionGraphBuilder builder = new ExecutionGraphBuilder();

    @Test
    void sequentialStageAddsImplicitDependencies() {
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        StageEntity stage = StageEntity.builder()
                .id(stageId)
                .pipelineId(pipelineId)
                .position(1)
                .name("build")
                .runPolicy("sequential")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        JobEntity first = JobEntity.builder()
                .id(UUID.randomUUID())
                .stageId(stageId)
                .position(1)
                .name("A")
                .jobType("build")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        JobEntity second = JobEntity.builder()
                .id(UUID.randomUUID())
                .stageId(stageId)
                .position(2)
                .name("B")
                .jobType("build")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        var graph = builder.build(List.of(stage), List.of(first, second), List.of());
        assertEquals(1, graph.dependencies().get(second.getId()).size());
        assertEquals(first.getId(), graph.dependencies().get(second.getId()).getFirst().dependsOnJobId());
        assertFalse(graph.dependencies().get(first.getId()).iterator().hasNext());
    }
}
