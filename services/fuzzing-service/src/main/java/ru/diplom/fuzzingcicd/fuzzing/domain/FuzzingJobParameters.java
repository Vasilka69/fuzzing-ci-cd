package ru.diplom.fuzzingcicd.fuzzing.domain;

public record FuzzingJobParameters(
        String targetArtifactUri,
        String sourceSnapshotUri,
        String targetCommand,
        String seedCorpusUri,
        String dictionaryUri,
        String mode,
        int budgetSeconds,
        int memoryLimitMb,
        String promptUri,
        LlmSettings llm,
        FuzzingPolicy policy
) {
}
