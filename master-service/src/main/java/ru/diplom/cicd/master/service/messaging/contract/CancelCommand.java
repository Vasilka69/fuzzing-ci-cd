package ru.diplom.cicd.master.service.messaging.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CancelCommand(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("messageId") UUID messageId,
        @JsonProperty("correlationId") UUID correlationId,
        @JsonProperty("pipelineRunId") UUID pipelineRunId,
        @JsonProperty("jobExecutionId") UUID jobExecutionId,
        @JsonProperty("reason") String reason,
        @JsonProperty("requestedBy") String requestedBy,
        @JsonProperty("gracePeriodSeconds") int gracePeriodSeconds,
        @JsonProperty("requestedAt") OffsetDateTime requestedAt
) {
}
