package ru.diplom.cicd.master.api.dto.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PipelineResponse(
        UUID id,
        UUID folderId,
        String name,
        String description,
        boolean isActive,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
