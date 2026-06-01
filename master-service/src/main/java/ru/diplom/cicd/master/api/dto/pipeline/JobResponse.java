package ru.diplom.cicd.master.api.dto.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID stageId,
        UUID jobTemplateId,
        int position,
        String name,
        String jobType,
        JsonNode params,
        String script,
        boolean isScriptPrimary,
        String condition,
        int timeoutSeconds,
        int maxAttempts,
        JsonNode resourceLimits,
        JsonNode sandboxPolicy,
        boolean continueOnError,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
