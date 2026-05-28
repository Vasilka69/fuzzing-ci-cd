package ru.diplom.fuzzingcicd.contracts.executor;

public enum ExecutorErrorType {
    VALIDATION_ERROR,
    USER_CONFIGURATION_ERROR,
    INFRASTRUCTURE_ERROR,
    ENGINE_STARTUP_ERROR,
    ENGINE_TIMEOUT,
    FUZZING_CRASH_FOUND,
    FUZZING_HANG_FOUND
}
