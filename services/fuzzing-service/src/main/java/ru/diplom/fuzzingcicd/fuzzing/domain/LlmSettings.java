package ru.diplom.fuzzingcicd.fuzzing.domain;

public record LlmSettings(
        String endpointRef,
        String model,
        Double temperature
) {
}
