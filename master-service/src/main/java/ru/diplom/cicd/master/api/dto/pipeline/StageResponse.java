package ru.diplom.cicd.master.api.dto.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StageResponse(
        UUID id,
        UUID pipelineId,
        int position,
        String name,
        String description,
        String runPolicy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
