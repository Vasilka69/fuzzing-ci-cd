package ru.diplom.cicd.master.service.messaging.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobMessage(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("messageId") UUID messageId,
        @JsonProperty("correlationId") UUID correlationId,
        @JsonProperty("pipelineRunId") UUID pipelineRunId,
        @JsonProperty("pipelineId") UUID pipelineId,
        @JsonProperty("stageId") UUID stageId,
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("jobExecutionId") UUID jobExecutionId,
        @JsonProperty("jobType") String jobType,
        @JsonProperty("templatePath") String templatePath,
        @JsonProperty("attempt") int attempt,
        @JsonProperty("maxAttempts") int maxAttempts,
        @JsonProperty("timeoutSeconds") int timeoutSeconds,
        @JsonProperty("resourceLimits") Map<String, Object> resourceLimits,
        @JsonProperty("workspacePolicy") Map<String, Object> workspacePolicy,
        @JsonProperty("inputs") Map<String, Object> inputs,
        @JsonProperty("params") Map<String, Object> params,
        @JsonProperty("secrets") Map<String, Object> secrets,
        @JsonProperty("createdAt") OffsetDateTime createdAt
) {
}
