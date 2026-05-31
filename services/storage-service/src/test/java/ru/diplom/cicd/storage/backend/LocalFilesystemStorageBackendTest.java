package ru.diplom.cicd.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        Map.of("vcsType", "git"),
                        "16a0eeb0791b6c92451fd284dd9f599e0a7dbe7f6ebea6e2d2d06c7f74aec112"));

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
    void saveRejectsExpectedSha256Mismatch() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "actual");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));
        StorageSaveRequest request = new StorageSaveRequest(
                "artifacts/source.txt",
                "artifact",
                "source.txt",
                "text/plain",
                Map.of(),
                "0000000000000000000000000000000000000000000000000000000000000000");

        StorageChecksumMismatchException exception =
                assertThrows(StorageChecksumMismatchException.class, () -> backend.save(source, request));

        assertEquals(
                "SHA-256 checksum артефакта не совпадает для storage://artifacts/source.txt: "
                        + "expected=0000000000000000000000000000000000000000000000000000000000000000, "
                        + "actual=e5c6fde86910ded72db5cc7afc32f850440d4ef7caa5dbb69f5bdc0d3e39cb3b",
                exception.getMessage());
        assertTrue(Files.notExists(tempDir.resolve("storage/artifacts/source.txt")));
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
    void cleanupDeletesSingleArtifactAndIsIdempotent() throws IOException {
        Path artifact = tempDir.resolve("storage/temporary/job-1/output.txt");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "temporary");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));

        StorageCleanupResult firstResult = backend.cleanup("temporary/job-1/output.txt", false);
        StorageCleanupResult secondResult = backend.cleanup("temporary/job-1/output.txt", false);

        assertEquals("temporary/job-1/output.txt", firstResult.namespacePath());
        assertEquals("storage://temporary/job-1/output.txt", firstResult.storageUri());
        assertTrue(firstResult.deleted());
        assertEquals(1L, firstResult.deletedCount());
        assertEquals(9L, firstResult.bytesFreed());
        assertTrue(Files.notExists(artifact));
        assertFalse(secondResult.deleted());
        assertEquals(0L, secondResult.deletedCount());
        assertEquals(0L, secondResult.bytesFreed());
    }

    @Test
    void cleanupDeletesDirectoryOnlyWhenRecursiveRequested() throws IOException {
        Path namespace = tempDir.resolve("storage/temporary/job-2");
        Files.createDirectories(namespace.resolve("nested"));
        Files.writeString(namespace.resolve("a.txt"), "alpha");
        Files.writeString(namespace.resolve("nested/b.txt"), "beta");
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));

        StorageCleanupResult result = backend.cleanup("temporary/job-2", true);

        assertTrue(result.deleted());
        assertEquals(4L, result.deletedCount());
        assertEquals(9L, result.bytesFreed());
        assertTrue(Files.notExists(namespace));
    }

    @Test
    void cleanupRejectsDirectoryWithoutRecursiveFlag() throws IOException {
        Path namespace = tempDir.resolve("storage/temporary/job-3");
        Files.createDirectories(namespace);
        LocalFilesystemStorageBackend backend = new LocalFilesystemStorageBackend(tempDir.resolve("storage"));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> backend.cleanup("temporary/job-3", false));

        assertEquals(
                "Storage namespace является директорией, для удаления дерева нужен recursive=true: "
                        + "storage://temporary/job-3",
                exception.getMessage());
        assertTrue(Files.isDirectory(namespace));
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
