package ru.diplom.cicd.master.api.dto.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        String description,
        UUID parentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
