package ru.diplom.cicd.storage.api;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.storage.backend.LocalFilesystemStorageBackend;

@RestController
@RequestMapping("/artifacts")
public final class StorageArtifactController {

    private final LocalFilesystemStorageBackend storageBackend;

    public StorageArtifactController(LocalFilesystemStorageBackend storageBackend) {
        this.storageBackend = storageBackend;
    }

    @PutMapping("/{*namespacePath}")
    public ResponseEntity<ArtifactDescriptor> upload(
            @PathVariable String namespacePath,
            @RequestHeader HttpHeaders headers,
            HttpServletRequest request,
            InputStream body)
            throws IOException {
        String normalizedNamespacePath = StorageArtifactHttpRequestUtils.normalizeCapturedPath(namespacePath);
        Path temporaryFile = Files.createTempFile("cicd-storage-upload-", ".tmp");
        try {
            Files.copy(body, temporaryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ArtifactDescriptor artifact = storageBackend.save(
                    temporaryFile,
                    StorageArtifactHttpRequestUtils.saveRequest(normalizedNamespacePath, headers, request));
            return ResponseEntity.ok(artifact);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    @GetMapping("/{*namespacePath}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String namespacePath) {
        String normalizedNamespacePath = StorageArtifactHttpRequestUtils.normalizeCapturedPath(namespacePath);
        Path artifactPath = storageBackend.load(normalizedNamespacePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(StorageArtifactHttpRequestUtils.downloadFileName(normalizedNamespacePath))
                                .build()
                                .toString())
                .body(new FileSystemResource(artifactPath));
    }
}
