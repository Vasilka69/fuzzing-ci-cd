package ru.diplom.cicd.executor.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

class HttpStorageClientTest {

    @TempDir
    private Path tempDir;

    @Test
    void uploadSendsArtifactToStorageEndpointAndReturnsDescriptor() throws IOException {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        AtomicReference<String> checksumHeader = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpStorageClient.HttpStorageTransport transport = new HttpStorageClient.HttpStorageTransport() {
            @Override
            public CompletionStage<Integer> upload(
                    URI uri, Path sourcePath, Map<String, String> headers, Duration timeout) {
                try {
                    requestUri.set(uri);
                    checksumHeader.set(headers.get("X-CICD-Checksum-Sha256"));
                    body.set(Files.readString(sourcePath));
                    return CompletableFuture.completedFuture(201);
                } catch (IOException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }

            @Override
            public CompletionStage<Integer> download(URI uri, Path targetPath, Duration timeout) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        Path source = tempDir.resolve("artifact.txt");
        Files.writeString(source, "hello");
        HttpStorageClient client = new HttpStorageClient(transport, baseUri(), Duration.ofSeconds(5));

        ArtifactDescriptor descriptor = client.upload(new StorageUploadRequest(
                        source, "pipelines/pipeline-1/runs/run-1/artifact.txt", "report", null, "text/plain", null))
                .toCompletableFuture()
                .join();

        assertEquals(
                "http://storage.local/api/artifacts/pipelines/pipeline-1/runs/run-1/artifact.txt",
                requestUri.get().toString());
        assertEquals("hello", body.get());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", checksumHeader.get());
        assertEquals("storage://pipelines/pipeline-1/runs/run-1/artifact.txt", descriptor.uri());
        assertEquals("artifact.txt", descriptor.name());
        assertEquals("text/plain", descriptor.contentType());
    }

    @Test
    void downloadWritesResponseBodyToTargetPath() throws IOException {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        HttpStorageClient.HttpStorageTransport transport = new HttpStorageClient.HttpStorageTransport() {
            @Override
            public CompletionStage<Integer> upload(
                    URI uri, Path sourcePath, Map<String, String> headers, Duration timeout) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public CompletionStage<Integer> download(URI uri, Path targetPath, Duration timeout) {
                try {
                    requestUri.set(uri);
                    Files.writeString(targetPath, "payload");
                    return CompletableFuture.completedFuture(200);
                } catch (IOException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }
        };
        HttpStorageClient client = new HttpStorageClient(transport, baseUri(), Duration.ofSeconds(5));

        Path downloaded = client.download(new StorageDownloadRequest(
                        "storage://pipelines/pipeline-1/runs/run-1/payload.txt", tempDir.resolve("out/payload.txt")))
                .toCompletableFuture()
                .join();

        assertEquals(
                "http://storage.local/api/artifacts/pipelines/pipeline-1/runs/run-1/payload.txt",
                requestUri.get().toString());
        assertEquals(tempDir.resolve("out/payload.txt").toAbsolutePath().normalize(), downloaded);
        assertEquals("payload", Files.readString(downloaded));
    }

    @Test
    void uploadFailsWhenStorageEndpointReturnsNonSuccessStatus() throws IOException {
        HttpStorageClient.HttpStorageTransport transport = new HttpStorageClient.HttpStorageTransport() {
            @Override
            public CompletionStage<Integer> upload(
                    URI uri, Path sourcePath, Map<String, String> headers, Duration timeout) {
                return CompletableFuture.completedFuture(500);
            }

            @Override
            public CompletionStage<Integer> download(URI uri, Path targetPath, Duration timeout) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        Path source = tempDir.resolve("artifact.txt");
        Files.writeString(source, "hello");
        HttpStorageClient client = new HttpStorageClient(transport, baseUri(), Duration.ofSeconds(5));

        CompletionException error = assertFailed(() -> client.upload(new StorageUploadRequest(
                        source, "pipelines/pipeline-1/runs/run-1/artifact.txt", "report", null, null, null))
                .toCompletableFuture()
                .join());

        StorageClientException cause = assertInstanceOf(StorageClientException.class, error.getCause());
        assertTrue(cause.getMessage().contains("Storage service вернул HTTP 500"));
    }

    private URI baseUri() {
        return URI.create("http://storage.local/api/");
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
