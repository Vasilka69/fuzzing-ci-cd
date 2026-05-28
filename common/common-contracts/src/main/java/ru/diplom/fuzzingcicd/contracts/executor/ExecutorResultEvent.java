package ru.diplom.fuzzingcicd.contracts.executor;

import java.util.Map;

public record ExecutorResultEvent(
        String schemaVersion,
        String messageId,
        String jobExecutionId,
        String jobType,
        ExecutorJobStatus status,
        JobError error,
        Map<String, Object> outputs
) {
    public ExecutorResultEvent {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
