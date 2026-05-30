package ru.diplom.cicd.executor.core.job;

import java.util.Map;
import java.util.Objects;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Typed exception для контролируемого завершения executor job с единым {@code error.type}.
 */
public final class ExecutorJobException extends RuntimeException {

    private final ErrorType errorType;
    private final String code;
    private final String details;
    private final transient Map<String, Object> metadata;
    private final ExecutionStatus status;

    public ExecutorJobException(
            ErrorType errorType,
            String code,
            String message,
            String details,
            Map<String, Object> metadata,
            ExecutionStatus status) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType");
        this.code = code == null || code.isBlank() ? "executor.job.failed" : code;
        this.details = details;
        this.metadata = ContractCollections.immutableMap(metadata);
        this.status = Objects.requireNonNull(status, "status");
    }

    public static ExecutorJobException validation(String message) {
        return new ExecutorJobException(
                ErrorType.VALIDATION_ERROR, "executor.job.validation", message, null, Map.of(), ExecutionStatus.FAILED);
    }

    public ErrorType errorType() {
        return errorType;
    }

    public String code() {
        return code;
    }

    public String details() {
        return details;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public ExecutionStatus status() {
        return status;
    }
}
