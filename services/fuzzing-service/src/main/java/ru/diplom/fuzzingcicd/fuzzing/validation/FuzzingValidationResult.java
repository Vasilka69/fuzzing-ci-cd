package ru.diplom.fuzzingcicd.fuzzing.validation;

import java.util.List;

public record FuzzingValidationResult(List<FuzzingValidationError> errors) {

    public FuzzingValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrorOn(String field) {
        return errors.stream().anyMatch(error -> error.field().equals(field));
    }
}
