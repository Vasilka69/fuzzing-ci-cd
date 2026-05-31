package ru.diplom.cicd.storage.backend;

import java.util.Map;

public record StorageSaveRequest(
        String destinationPath, String artifactType, String name, String contentType, Map<String, Object> metadata) {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public StorageSaveRequest {
        if (destinationPath == null || destinationPath.isBlank()) {
            throw new IllegalArgumentException("Storage namespace артефакта должен быть непустым");
        }
        if (name == null || name.isBlank()) {
            name = "artifact";
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
