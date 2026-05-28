package ru.diplom.fuzzingcicd.kafka;

import java.util.ArrayList;
import java.util.List;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;

public final class ExecutorJobEnvelopeValidator {

    public List<String> validate(ExecutorJobEnvelope envelope) {
        List<String> errors = new ArrayList<>();
        if (envelope == null) {
            errors.add("envelope must not be null");
            return List.copyOf(errors);
        }
        requireText(envelope.schemaVersion(), "schema_version", errors);
        if (!ExecutorJobEnvelope.CURRENT_SCHEMA_VERSION.equals(envelope.schemaVersion())) {
            errors.add("schema_version is not supported");
        }
        requireText(envelope.messageId(), "message_id", errors);
        requireText(envelope.jobExecutionId(), "job_execution_id", errors);
        requireText(envelope.jobType(), "job_type", errors);
        requireText(envelope.templatePath(), "template_path", errors);
        if (envelope.attempt() < 0) {
            errors.add("attempt must not be negative");
        }
        if (envelope.timeoutSeconds() <= 0) {
            errors.add("timeout_seconds must be positive");
        }
        if (envelope.resourceLimits() == null) {
            errors.add("resource_limits must not be null");
        }
        return List.copyOf(errors);
    }

    private static void requireText(String value, String fieldName, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " must not be blank");
        }
    }
}
