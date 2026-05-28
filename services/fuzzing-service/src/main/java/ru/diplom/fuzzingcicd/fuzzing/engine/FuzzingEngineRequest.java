package ru.diplom.fuzzingcicd.fuzzing.engine;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingMode;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.domain.LlmSettings;
import ru.diplom.fuzzingcicd.fuzzing.domain.TargetCommand;

public record FuzzingEngineRequest(
        String jobExecutionId,
        Path workspace,
        String targetArtifactUri,
        String sourceSnapshotUri,
        TargetCommand targetCommand,
        String seedCorpusUri,
        String dictionaryUri,
        FuzzingMode mode,
        Duration budget,
        int memoryLimitMb,
        String promptUri,
        LlmSettings llm,
        FuzzingPolicy policy
) {
    public FuzzingEngineRequest {
        jobExecutionId = requireText(jobExecutionId, "jobExecutionId");
        workspace = Objects.requireNonNull(workspace, "workspace must not be null");
        targetCommand = Objects.requireNonNull(targetCommand, "targetCommand must not be null");
        mode = Objects.requireNonNull(mode, "mode must not be null");
        budget = Objects.requireNonNull(budget, "budget must not be null");
        policy = Objects.requireNonNull(policy, "policy must not be null");
        if (budget.isZero() || budget.isNegative()) {
            throw new IllegalArgumentException("budget must be positive");
        }
        if (memoryLimitMb < 1) {
            throw new IllegalArgumentException("memoryLimitMb must be greater than zero");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
