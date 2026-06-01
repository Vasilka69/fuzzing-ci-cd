package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.ArtifactEntity;
import ru.diplom.cicd.master.domain.entity.StorageObjectEntity;
import ru.diplom.cicd.master.repository.ArtifactRepository;
import ru.diplom.cicd.master.repository.StorageObjectRepository;
import ru.diplom.cicd.master.service.messaging.contract.ArtifactDescriptor;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void register(UUID pipelineRunId, UUID jobExecutionId, List<ArtifactDescriptor> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        for (ArtifactDescriptor descriptor : artifacts) {
            ObjectNode metadata = descriptor.metadata() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.valueToTree(descriptor.metadata());
            ArtifactEntity artifact = ArtifactEntity.builder()
                    .id(descriptor.artifactId() == null ? UUID.randomUUID() : descriptor.artifactId())
                    .pipelineRunId(pipelineRunId)
                    .jobExecutionId(jobExecutionId)
                    .artifactType(descriptor.type())
                    .name(descriptor.name())
                    .uri(descriptor.uri())
                    .sizeBytes(descriptor.sizeBytes())
                    .sha256(descriptor.sha256())
                    .metadata(metadata)
                    .retentionPolicy("default")
                    .status("available")
                    .checksumVerified(false)
                    .createdAt(OffsetDateTime.now())
                    .build();
            ArtifactEntity saved = artifactRepository.save(artifact);
            registerStorageObject(saved, descriptor, metadata);
        }
    }

    private void registerStorageObject(ArtifactEntity artifact, ArtifactDescriptor descriptor, ObjectNode metadata) {
        if (descriptor == null || descriptor.uri() == null || descriptor.uri().isBlank()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        StorageLocation location = parseStorageLocation(descriptor.uri());
        StorageObjectEntity storageObject = storageObjectRepository.findByStorageUri(descriptor.uri())
                .orElseGet(() -> StorageObjectEntity.builder()
                        .id(UUID.randomUUID())
                        .storageUri(descriptor.uri())
                        .backend(location.backend())
                        .uploadStatus("initiated")
                        .retentionPolicy("default")
                        .metadata(objectMapper.createObjectNode())
                        .createdAt(now)
                        .build());

        storageObject.setArtifactId(artifact.getId());
        storageObject.setPipelineRunId(artifact.getPipelineRunId());
        storageObject.setJobExecutionId(artifact.getJobExecutionId());
        storageObject.setBackend(location.backend());
        storageObject.setBucket(location.bucket());
        storageObject.setObjectKey(location.objectKey());
        storageObject.setUploadStatus("completed");
        storageObject.setSizeBytes(descriptor.sizeBytes());
        storageObject.setSha256(descriptor.sha256());
        storageObject.setContentType(readContentType(metadata));
        storageObject.setRetentionPolicy("default");
        storageObject.setMetadata(metadata == null ? objectMapper.createObjectNode() : metadata.deepCopy());
        storageObject.setCompletedAt(now);
        storageObjectRepository.save(storageObject);
    }

    private String readContentType(ObjectNode metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata.hasNonNull("contentType")) {
            return metadata.get("contentType").asText(null);
        }
        if (metadata.hasNonNull("content_type")) {
            return metadata.get("content_type").asText(null);
        }
        return null;
    }

    private StorageLocation parseStorageLocation(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            return new StorageLocation("uri", null, null);
        }
        try {
            URI uri = new URI(rawUri);
            String backend = uri.getScheme() == null || uri.getScheme().isBlank()
                    ? "uri"
                    : uri.getScheme().toLowerCase();
            String bucket = uri.getHost();
            String objectKey = normalizePath(uri.getPath());

            if ((bucket == null || bucket.isBlank()) && uri.getSchemeSpecificPart() != null) {
                String ssp = uri.getSchemeSpecificPart();
                if (ssp.startsWith("//")) {
                    String withoutSlashes = ssp.substring(2);
                    int slashIndex = withoutSlashes.indexOf('/');
                    if (slashIndex >= 0) {
                        bucket = withoutSlashes.substring(0, slashIndex);
                        objectKey = normalizePath(withoutSlashes.substring(slashIndex));
                    } else {
                        bucket = withoutSlashes;
                    }
                }
            }
            return new StorageLocation(backend, blankToNull(bucket), blankToNull(objectKey));
        } catch (URISyntaxException ex) {
            return new StorageLocation("uri", null, null);
        }
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        return normalized.isBlank() ? null : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record StorageLocation(String backend, String bucket, String objectKey) {
    }
}
