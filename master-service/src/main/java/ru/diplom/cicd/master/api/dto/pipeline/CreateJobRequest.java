package ru.diplom.cicd.master.api.dto.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateJobRequest(
        UUID jobTemplateId,
        @NotNull Integer position,
        @NotBlank String name,
        @NotBlank String jobType,
        JsonNode params,
        String script,
        Boolean isScriptPrimary,
        String condition,
        Integer timeoutSeconds,
        Integer maxAttempts,
        JsonNode resourceLimits,
        JsonNode sandboxPolicy,
        Boolean continueOnError
) {
}
