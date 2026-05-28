package ru.diplom.fuzzingcicd.contracts.executor;

import java.util.Map;

public record ExecutorJobEnvelope(
        String schemaVersion,
        String messageId,
        String jobExecutionId,
        String jobType,
        String templatePath,
        int attempt,
        int timeoutSeconds,
        ResourceLimits resourceLimits,
        Map<String, Object> parameters
) {
    public static final String CURRENT_SCHEMA_VERSION = "1";

    public ExecutorJobEnvelope {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
