package ru.diplom.fuzzingcicd.contracts.executor;

import java.util.Objects;

public record JobError(
        ExecutorErrorType type,
        String code,
        String message
) {
    public JobError {
        type = Objects.requireNonNull(type, "type must not be null");
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
