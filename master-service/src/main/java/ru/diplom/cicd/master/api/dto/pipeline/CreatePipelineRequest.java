package ru.diplom.cicd.master.api.dto.pipeline;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreatePipelineRequest(
        UUID folderId,
        @NotBlank String name,
        String description
) {
}
