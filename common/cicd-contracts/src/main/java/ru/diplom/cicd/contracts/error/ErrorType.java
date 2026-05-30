package ru.diplom.cicd.contracts.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Тип ошибки executor-а из единого внешнего словаря `error.type`.
 */
public enum ErrorType {
    VALIDATION_ERROR("validation_error"),
    USER_CODE_ERROR("user_code_error"),
    INFRASTRUCTURE_ERROR("infrastructure_error"),
    TIMEOUT("timeout"),
    CANCELED("canceled"),
    SECURITY_ERROR("security_error"),
    FUZZING_CRASH_FOUND("fuzzing_crash_found"),
    CANCEL_FAILED("cancel_failed"),
    UNKNOWN("unknown");

    private final String wireValue;

    ErrorType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ErrorType fromWireValue(String wireValue) {
        return Arrays.stream(values())
                .filter(value -> value.wireValue.equals(wireValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный тип ошибки executor-а: " + wireValue));
    }
}
