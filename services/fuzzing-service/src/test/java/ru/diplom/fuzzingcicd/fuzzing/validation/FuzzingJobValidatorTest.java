package ru.diplom.fuzzingcicd.fuzzing.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.ResourceLimits;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingJobParameters;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.domain.LlmSettings;

class FuzzingJobValidatorTest {

    private final FuzzingJobValidator validator = new FuzzingJobValidator();

    @Test
    void acceptsValidFakeModeJob() {
        FuzzingValidationResult result = validator.validate(validEnvelope(), validParameters());

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsShellSyntaxInTargetCommand() {
        FuzzingValidationResult result = validator.validate(
                validEnvelope(),
                parameters("./target @@; curl http://example.test", "fake", null, 60, 512, null)
        );

        assertThat(result.hasErrorOn("target_command")).isTrue();
    }

    @Test
    void rejectsRealLlmModeWithoutConnectionRef() {
        FuzzingValidationResult result = validator.validate(
                validEnvelope(),
                parameters("./target @@", "real_llm", new LlmSettings("https://llm.local", "model", 0.7), 60, 512, null)
        );

        assertThat(result.hasErrorOn("llm.endpoint_ref")).isTrue();
    }

    @Test
    void rejectsBudgetThatExceedsEnvelopeTimeout() {
        FuzzingValidationResult result = validator.validate(
                validEnvelope(),
                parameters("./target @@", "fake", null, 121, 512, null)
        );

        assertThat(result.hasErrorOn("budget_seconds")).isTrue();
    }

    @Test
    void rejectsMemoryThatExceedsEnvelopeLimit() {
        FuzzingValidationResult result = validator.validate(
                validEnvelope(),
                parameters("./target @@", "fake", null, 60, 2048, null)
        );

        assertThat(result.hasErrorOn("memory_limit_mb")).isTrue();
    }

    @Test
    void rejectsAmbiguousTargetInput() {
        FuzzingValidationResult result = validator.validate(
                validEnvelope(),
                parameters("./target @@", "fake", null, 60, 512, "storage://snapshots/source.zip")
        );

        assertThat(result.hasErrorOn("target_artifact_uri")).isTrue();
    }

    private ExecutorJobEnvelope validEnvelope() {
        return new ExecutorJobEnvelope(
                "1",
                "message-1",
                "job-1",
                "fuzzing",
                "templates/fuzzing/default",
                1,
                120,
                new ResourceLimits(1_000, 1024, 2048),
                Map.of()
        );
    }

    private FuzzingJobParameters validParameters() {
        return parameters("./target @@", "fake", null, 60, 512, null);
    }

    private FuzzingJobParameters parameters(
            String targetCommand,
            String mode,
            LlmSettings llm,
            int budgetSeconds,
            int memoryLimitMb,
            String sourceSnapshotUri
    ) {
        return new FuzzingJobParameters(
                "storage://artifacts/target",
                sourceSnapshotUri,
                targetCommand,
                "storage://fuzzing/seeds.zip",
                "storage://fuzzing/dict.txt",
                mode,
                budgetSeconds,
                memoryLimitMb,
                null,
                llm,
                new FuzzingPolicy(true, false, 1, 10_000, true)
        );
    }
}
