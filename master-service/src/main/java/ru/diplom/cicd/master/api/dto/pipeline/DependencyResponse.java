package ru.diplom.cicd.master.api.dto.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DependencyResponse(
        UUID id,
        UUID jobId,
        UUID dependsOnJobId,
        String condition,
        OffsetDateTime createdAt
) {
}
