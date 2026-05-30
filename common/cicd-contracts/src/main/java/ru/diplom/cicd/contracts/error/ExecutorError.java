package ru.diplom.cicd.contracts.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Структурированное описание ошибки executor-а для поля `error` в result event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutorError(ErrorType type, String code, String message, String details, Map<String, Object> metadata) {

    public ExecutorError {
        metadata = ContractCollections.immutableMap(metadata);
    }
}
