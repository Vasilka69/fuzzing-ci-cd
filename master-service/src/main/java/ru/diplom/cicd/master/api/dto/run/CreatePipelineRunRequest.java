package ru.diplom.cicd.master.api.dto.run;

import com.fasterxml.jackson.databind.JsonNode;

public record CreatePipelineRunRequest(
        String triggerType,
        JsonNode triggerPayload
) {
}
