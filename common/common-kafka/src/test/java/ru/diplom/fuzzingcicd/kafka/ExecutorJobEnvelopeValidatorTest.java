package ru.diplom.fuzzingcicd.kafka;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.ResourceLimits;

class ExecutorJobEnvelopeValidatorTest {

    private final ExecutorJobEnvelopeValidator validator = new ExecutorJobEnvelopeValidator();

    @Test
    void acceptsValidEnvelope() {
        ExecutorJobEnvelope envelope = new ExecutorJobEnvelope(
                ExecutorJobEnvelope.CURRENT_SCHEMA_VERSION,
                "message-1",
                "job-1",
                "build",
                "templates/build.yml",
                1,
                300,
                new ResourceLimits(1000, 512, 1024),
                Map.of()
        );

        assertTrue(validator.validate(envelope).isEmpty());
    }

    @Test
    void rejectsMissingRequiredFields() {
        ExecutorJobEnvelope envelope = new ExecutorJobEnvelope(
                "2",
                "",
                null,
                "build",
                "templates/build.yml",
                -1,
                0,
                null,
                Map.of()
        );

        var errors = validator.validate(envelope);

        assertTrue(errors.contains("schema_version is not supported"));
        assertTrue(errors.contains("message_id must not be blank"));
        assertTrue(errors.contains("job_execution_id must not be blank"));
        assertTrue(errors.contains("attempt must not be negative"));
        assertTrue(errors.contains("timeout_seconds must be positive"));
        assertTrue(errors.contains("resource_limits must not be null"));
    }
}
