package ru.diplom.cicd.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ExecutorError;
import ru.diplom.cicd.contracts.internal.ContractCollections;
import ru.diplom.cicd.contracts.job.JobType;

/**
 * Kafka/OpenSearch event message, который executor публикует как результат обработки job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutorEventMessage(
        int schemaVersion,
        UUID messageId,
        UUID correlationId,
        UUID pipelineRunId,
        UUID pipelineId,
        UUID stageId,
        UUID jobId,
        UUID jobExecutionId,
        JobType jobType,
        String templatePath,
        EventType eventType,
        ExecutionStatus status,
        int attempt,
        String workerId,
        Long durationMs,
        List<ArtifactDescriptor> artifacts,
        Map<String, Object> metrics,
        String summary,
        ExecutorError error,
        String logs,
        Map<String, Object> additionalData) {

    public ExecutorEventMessage {
        artifacts = ContractCollections.immutableList(artifacts);
        metrics = ContractCollections.immutableMap(metrics);
        additionalData = ContractCollections.immutableMap(additionalData);
    }
}
