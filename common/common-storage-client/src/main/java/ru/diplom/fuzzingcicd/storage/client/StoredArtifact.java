package ru.diplom.fuzzingcicd.storage.client;

import java.net.URI;
import java.util.Map;

public record StoredArtifact(
        URI uri,
        long sizeBytes,
        String sha256,
        Map<String, String> metadata
) {
    public StoredArtifact {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
