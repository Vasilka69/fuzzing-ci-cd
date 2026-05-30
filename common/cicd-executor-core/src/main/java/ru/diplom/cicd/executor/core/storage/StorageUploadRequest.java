package ru.diplom.cicd.executor.core.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record StorageUploadRequest(
        Path sourcePath,
        String destinationPath,
        String artifactType,
        String name,
        String contentType,
        Map<String, Object> metadata) {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public StorageUploadRequest {
        Objects.requireNonNull(sourcePath, "Не задан исходный файл артефакта");
        if (destinationPath == null || destinationPath.isBlank()) {
            throw new IllegalArgumentException("Storage namespace артефакта должен быть непустым");
        }
        if (name == null || name.isBlank()) {
            Path fileName = sourcePath.getFileName();
            name = fileName == null ? "artifact" : fileName.toString();
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
