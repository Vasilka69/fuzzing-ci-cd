package ru.diplom.cicd.master.domain.enums;

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

    private final String value;

    ErrorType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ErrorType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ErrorType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
