package ru.diplom.cicd.contracts.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.diplom.cicd.contracts.internal.ContractCollections;
import ru.diplom.cicd.contracts.security.SandboxPolicy;

/**
 * Kafka job message, который master-service публикует executor-у.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobMessage(
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
        int attempt,
        int maxAttempts,
        long timeoutSeconds,
        ResourceLimits resourceLimits,
        WorkspacePolicy workspacePolicy,
        SandboxPolicy sandboxPolicy,
        Map<String, Object> inputs,
        Map<String, Object> params,
        Map<String, Object> secrets,
        Instant createdAt) {

    public JobMessage {
        resourceLimits = resourceLimits == null ? ResourceLimits.empty() : resourceLimits;
        inputs = ContractCollections.immutableMap(inputs);
        params = ContractCollections.immutableMap(params);
        secrets = ContractCollections.immutableMap(secrets);
    }
}
