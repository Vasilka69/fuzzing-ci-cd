package ru.diplom.cicd.executor.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

class LocalStorageClientTest {

    @TempDir
    private Path tempDir;

    @Test
    void uploadStoresArtifactAndReturnsDescriptor() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "hello");
        LocalStorageClient client = new LocalStorageClient(tempDir.resolve("storage"));

        ArtifactDescriptor descriptor = client.upload(new StorageUploadRequest(
                        source,
                        "pipelines/pipeline-1/runs/run-1/artifact.txt",
                        "report",
                        "artifact.txt",
                        "text/plain",
                        Map.of("kind", "demo")))
                .toCompletableFuture()
                .join();

        assertEquals("storage://pipelines/pipeline-1/runs/run-1/artifact.txt", descriptor.uri());
        assertEquals("report", descriptor.artifactType());
        assertEquals("artifact.txt", descriptor.name());
        assertEquals("text/plain", descriptor.contentType());
        assertEquals(5L, descriptor.sizeBytes());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", descriptor.checksumSha256());
        assertEquals(Map.of("kind", "demo"), descriptor.metadata());
        assertEquals(
                "hello", Files.readString(tempDir.resolve("storage/pipelines/pipeline-1/runs/run-1/artifact.txt")));
    }

    @Test
    void downloadCopiesArtifactByStorageUri() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "payload");
        LocalStorageClient client = new LocalStorageClient(tempDir.resolve("storage"));
        ArtifactDescriptor descriptor = client.upload(new StorageUploadRequest(
                        source, "pipelines/pipeline-1/runs/run-1/payload.txt", "payload", null, null, null))
                .toCompletableFuture()
                .join();

        Path downloaded = client.download(
                        new StorageDownloadRequest(descriptor.uri(), tempDir.resolve("out/payload.txt")))
                .toCompletableFuture()
                .join();

        assertEquals(tempDir.resolve("out/payload.txt").toAbsolutePath().normalize(), downloaded);
        assertEquals("payload", Files.readString(downloaded));
    }

    @Test
    void uploadIsIdempotentWhenExistingArtifactHasSameContent() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "same");
        LocalStorageClient client = new LocalStorageClient(tempDir.resolve("storage"));
        StorageUploadRequest request =
                new StorageUploadRequest(source, "pipelines/pipeline-1/runs/run-1/same.txt", "log", null, null, null);

        ArtifactDescriptor firstDescriptor =
                client.upload(request).toCompletableFuture().join();
        ArtifactDescriptor secondDescriptor =
                client.upload(request).toCompletableFuture().join();

        assertEquals(firstDescriptor.artifactId(), secondDescriptor.artifactId());
        assertEquals(firstDescriptor.checksumSha256(), secondDescriptor.checksumSha256());
    }

    @Test
    void uploadRejectsDifferentContentAtExistingStorageUri() throws IOException {
        Path firstSource = tempDir.resolve("first.txt");
        Path secondSource = tempDir.resolve("second.txt");
        Files.writeString(firstSource, "first");
        Files.writeString(secondSource, "second");
        LocalStorageClient client = new LocalStorageClient(tempDir.resolve("storage"));
        client.upload(new StorageUploadRequest(
                        firstSource, "pipelines/pipeline-1/runs/run-1/result.txt", "result", null, null, null))
                .toCompletableFuture()
                .join();

        CompletionException error = assertFailed(() -> client.upload(new StorageUploadRequest(
                        secondSource, "pipelines/pipeline-1/runs/run-1/result.txt", "result", null, null, null))
                .toCompletableFuture()
                .join());

        StorageClientException cause = assertInstanceOf(StorageClientException.class, error.getCause());
        assertEquals(
                "Артефакт уже существует с другим содержимым: "
                        + "storage://pipelines/pipeline-1/runs/run-1/result.txt",
                cause.getMessage());
    }

    @Test
    void uploadRejectsPathTraversal() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "evil");
        LocalStorageClient client = new LocalStorageClient(tempDir.resolve("storage"));

        CompletionException error = assertFailed(
                () -> client.upload(new StorageUploadRequest(source, "../outside.txt", "evil", null, null, null))
                        .toCompletableFuture()
                        .join());

        StorageClientException cause = assertInstanceOf(StorageClientException.class, error.getCause());
        assertEquals("Storage namespace выходит за пределы корня: ../outside.txt", cause.getMessage());
        assertTrue(Files.notExists(tempDir.resolve("outside.txt")));
    }

    private static CompletionException assertFailed(Runnable action) {
        try {
            action.run();
        } catch (CompletionException exception) {
            return exception;
        }
        return fail("Ожидалась ошибка CompletionException");
    }
}
