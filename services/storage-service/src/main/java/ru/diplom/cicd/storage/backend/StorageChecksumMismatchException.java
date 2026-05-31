package ru.diplom.cicd.storage.backend;

public final class StorageChecksumMismatchException extends RuntimeException {

    public StorageChecksumMismatchException(String message) {
        super(message);
    }
}
