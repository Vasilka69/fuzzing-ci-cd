package ru.diplom.fuzzingcicd.fuzzing.engine;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.stereotype.Component;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingJobParameters;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingMode;
import ru.diplom.fuzzingcicd.fuzzing.validation.TargetCommandParser;

@Component
public class FuzzingEngineRequestFactory {

    public FuzzingEngineRequest create(
            ExecutorJobEnvelope envelope,
            FuzzingJobParameters parameters,
            Path workspace
    ) {
        FuzzingMode mode = FuzzingMode.fromCode(parameters.mode())
                .orElseThrow(() -> new IllegalArgumentException("unsupported fuzzing mode: " + parameters.mode()));
        return new FuzzingEngineRequest(
                envelope.jobExecutionId(),
                workspace,
                parameters.targetArtifactUri(),
                parameters.sourceSnapshotUri(),
                TargetCommandParser.parse(parameters.targetCommand()),
                parameters.seedCorpusUri(),
                parameters.dictionaryUri(),
                mode,
                Duration.ofSeconds(parameters.budgetSeconds()),
                parameters.memoryLimitMb(),
                parameters.promptUri(),
                parameters.llm(),
                parameters.policy()
        );
    }
}
