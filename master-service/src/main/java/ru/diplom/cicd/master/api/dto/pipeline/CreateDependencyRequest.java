package ru.diplom.cicd.master.api.dto.pipeline;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDependencyRequest(
        @NotNull UUID dependsOnJobId,
        String condition
) {
}
