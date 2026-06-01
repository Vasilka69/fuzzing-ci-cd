package ru.diplom.cicd.master.api.dto.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStageRequest(
        @NotNull Integer position,
        @NotBlank String name,
        String description,
        String runPolicy
) {
}
