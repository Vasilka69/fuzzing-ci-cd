package ru.diplom.cicd.master.api.dto.pipeline;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateFolderRequest(
        @NotBlank String name,
        String description,
        UUID parentId
) {
}
