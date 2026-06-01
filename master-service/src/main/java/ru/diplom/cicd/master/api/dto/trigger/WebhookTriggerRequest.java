package ru.diplom.cicd.master.api.dto.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record WebhookTriggerRequest(
        UUID triggerId,
        UUID pipelineId,
        String eventSource,
        String externalEventId,
        String idempotencyKey,
        String ref,
        String commitHash,
        JsonNode payload
) {
}
