package ru.diplom.cicd.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.executor.core.storage.StorageClientException;

class LocalFilesystemStorageBackendTest {

    @TempDir
    private Path tempDir;

    @Test
    void saveStoresFileInsideStorageRootAndReturnsDescriptor() throws IOException {
        Path source = tempDir.resolve("source-snapshot.tar.gz");
        Files.writeString(source, "snapshot");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));

        ArtifactDescriptor artifact = backend.save(
                source,
                new StorageSaveRequest(
                        "source-snapshots/job-1/source-snapshot.tar.gz",
                        "source_snapshot",
                        "source-snapshot.tar.gz",
                        "application/gzip",
                        Map.of("vcsType", "git")));

        assertEquals("storage://source-snapshots/job-1/source-snapshot.tar.gz", artifact.uri());
        assertEquals("source_snapshot", artifact.artifactType());
        assertEquals("source-snapshot.tar.gz", artifact.name());
        assertEquals("application/gzip", artifact.contentType());
        assertEquals(8L, artifact.sizeBytes());
        assertEquals("16a0eeb0791b6c92451fd284dd9f599e0a7dbe7f6ebea6e2d2d06c7f74aec112", artifact.checksumSha256());
        assertEquals(Map.of("vcsType", "git"), artifact.metadata());
        assertEquals(
                "snapshot", Files.readString(tempDir.resolve("storage/source-snapshots/job-1/source-snapshot.tar.gz")));
    }

    @Test
    void saveIsIdempotentForSameStorageUriAndContent() throws IOException {
        Path source = tempDir.resolve("same.txt");
        Files.writeString(source, "same");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));
        StorageSaveRequest request =
                new StorageSaveRequest("artifacts/same.txt", "artifact", "same.txt", "text/plain", Map.of());

        ArtifactDescriptor firstArtifact = backend.save(source, request);
        ArtifactDescriptor secondArtifact = backend.save(source, request);

        assertEquals(firstArtifact.artifactId(), secondArtifact.artifactId());
        assertEquals(firstArtifact.checksumSha256(), secondArtifact.checksumSha256());
    }

    @Test
    void loadReturnsStoredArtifactPath() throws IOException {
        Path source = tempDir.resolve("download.txt");
        Files.writeString(source, "download");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));
        StorageSaveRequest request =
                new StorageSaveRequest("artifacts/download.txt", "artifact", "download.txt", "text/plain", Map.of());
        backend.save(source, request);

        Path artifactPath = backend.load("artifacts/download.txt");

        assertEquals("download", Files.readString(artifactPath));
        assertTrue(artifactPath.startsWith(
                tempDir.resolve("storage").toAbsolutePath().normalize()));
    }

    @Test
    void loadRejectsMissingArtifact() {
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));

        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> backend.load("artifacts/missing.txt"));

        assertEquals("Артефакт не найден: storage://artifacts/missing.txt", exception.getMessage());
    }

    @Test
    void saveRejectsPathTraversal() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "outside");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));
        StorageSaveRequest request = new StorageSaveRequest("../outside.txt", "artifact", "source.txt", null, null);

        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> backend.save(source, request));

        assertEquals("Storage namespace выходит за пределы корня: ../outside.txt", exception.getMessage());
        assertTrue(Files.notExists(tempDir.resolve("outside.txt")));
    }
}
