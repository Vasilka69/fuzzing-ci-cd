package ru.diplom.cicd.storage.backend;

import java.util.Map;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;

public record StorageSaveRequest(
        String destinationPath,
        String artifactType,
        String name,
        String contentType,
        Map<String, Object> metadata,
        String expectedChecksumSha256) {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public StorageSaveRequest(
            String destinationPath,
            String artifactType,
            String name,
            String contentType,
            Map<String, Object> metadata) {
        this(destinationPath, artifactType, name, contentType, metadata, null);
    }

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
        expectedChecksumSha256 = StorageChecksums.normalizeSha256(expectedChecksumSha256);
    }
}
