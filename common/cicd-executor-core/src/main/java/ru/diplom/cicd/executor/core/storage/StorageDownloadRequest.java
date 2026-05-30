package ru.diplom.cicd.executor.core.storage;

import java.nio.file.Path;
import java.util.Objects;

public record StorageDownloadRequest(String uri, Path targetPath) {

    public StorageDownloadRequest {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Storage URI должен быть непустым");
        }
        Objects.requireNonNull(targetPath, "Не задан целевой путь для скачивания артефакта");
    }
}
