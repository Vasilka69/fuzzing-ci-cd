package ru.diplom.cicd.master.service.messaging.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutorEventMessage(
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
        @JsonProperty("eventType") String eventType,
        @JsonProperty("status") String status,
        @JsonProperty("attempt") int attempt,
        @JsonProperty("workerId") String workerId,
        @JsonProperty("startedAt") OffsetDateTime startedAt,
        @JsonProperty("finishedAt") OffsetDateTime finishedAt,
        @JsonProperty("durationMs") Long durationMs,
        @JsonProperty("artifacts") List<ArtifactDescriptor> artifacts,
        @JsonProperty("metrics") Map<String, Object> metrics,
        @JsonProperty("summary") String summary,
        @JsonProperty("error") ExecutorError error,
        @JsonProperty("logs") String logs,
        @JsonProperty("additionalData") Map<String, Object> additionalData
) {
}
