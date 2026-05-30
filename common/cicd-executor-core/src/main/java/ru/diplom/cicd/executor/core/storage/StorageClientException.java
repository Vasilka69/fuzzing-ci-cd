package ru.diplom.cicd.executor.core.storage;

public final class StorageClientException extends RuntimeException {

    public StorageClientException(String message) {
        super(message);
    }

    public StorageClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
