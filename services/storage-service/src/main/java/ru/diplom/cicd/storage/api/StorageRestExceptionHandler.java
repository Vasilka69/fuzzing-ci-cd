package ru.diplom.cicd.storage.api;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.storage.backend.StorageChecksumMismatchException;

@RestControllerAdvice
public final class StorageRestExceptionHandler {

    @ExceptionHandler({
        IllegalArgumentException.class,
        StorageClientException.class,
        StorageChecksumMismatchException.class
    })
    public ResponseEntity<StorageApiError> handleStorageClientException(RuntimeException exception) {
        HttpStatus status = status(exception);
        return ResponseEntity.status(status).body(new StorageApiError(code(status), exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<StorageApiError> handleIoException(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StorageApiError("storage_io_error", "Не удалось обработать файл storage-service"));
    }

    private static HttpStatus status(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && message.startsWith("Артефакт не найден:")) {
            return HttpStatus.NOT_FOUND;
        }
        if (message != null && message.startsWith("Артефакт уже существует с другим содержимым:")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String code(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "artifact_not_found";
            case CONFLICT -> "artifact_conflict";
            default -> "invalid_storage_request";
        };
    }
}
