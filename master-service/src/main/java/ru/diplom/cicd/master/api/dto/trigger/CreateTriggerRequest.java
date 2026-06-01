package ru.diplom.cicd.master.api.dto.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTriggerRequest(
        @NotNull UUID pipelineId,
        @NotBlank String name,
        @NotBlank String triggerType,
        JsonNode config,
        Boolean isActive
) {
}
