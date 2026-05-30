package ru.diplom.cicd.contracts.artifact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.UUID;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Метаданные артефакта, который executor передает через URI, а не через Kafka payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactDescriptor(
        UUID artifactId,
        String artifactType,
        String name,
        String uri,
        String contentType,
        Long sizeBytes,
        String checksumSha256,
        Map<String, Object> metadata) {

    public ArtifactDescriptor {
        metadata = ContractCollections.immutableMap(metadata);
    }
}
