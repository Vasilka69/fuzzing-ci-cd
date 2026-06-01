package ru.diplom.cicd.master.api.dto.trigger;

import com.fasterxml.jackson.databind.JsonNode;

public record TriggerFireRequest(
        String externalEventId,
        String idempotencyKey,
        JsonNode payload
) {
}
