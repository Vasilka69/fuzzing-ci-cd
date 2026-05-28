package ru.diplom.fuzzingcicd.fuzzing.validation;

public record FuzzingValidationError(
        String field,
        String message
) {
}
