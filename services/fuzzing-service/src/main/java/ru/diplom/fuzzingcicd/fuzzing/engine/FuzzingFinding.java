package ru.diplom.fuzzingcicd.fuzzing.engine;

public record FuzzingFinding(
        String artifactUri,
        String relativePath,
        long sizeBytes
) {
}
