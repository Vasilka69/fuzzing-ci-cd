package ru.diplom.cicd.executor.core.job;

import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ExecutorError;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Результат сервисной части job, который общий handler преобразует в executor events.
 */
public record ExecutorJobResult(
        ExecutionStatus status,
        String summary,
        List<ArtifactDescriptor> artifacts,
        Map<String, Object> metrics,
        String logs,
        ExecutorError error,
        Map<String, Object> additionalData) {

    public ExecutorJobResult {
        status = status == null ? ExecutionStatus.SUCCESS : status;
        artifacts = ContractCollections.immutableList(artifacts);
        metrics = ContractCollections.immutableMap(metrics);
        additionalData = ContractCollections.immutableMap(additionalData);
    }

    public static ExecutorJobResult success(String summary) {
        return success(summary, List.of(), null);
    }

    public static ExecutorJobResult success(String summary, List<ArtifactDescriptor> artifacts, String logs) {
        return new ExecutorJobResult(ExecutionStatus.SUCCESS, summary, artifacts, Map.of(), logs, null, Map.of());
    }

    public static ExecutorJobResult failure(String summary, ExecutorError error, String logs) {
        return new ExecutorJobResult(ExecutionStatus.FAILED, summary, List.of(), Map.of(), logs, error, Map.of());
    }
}
