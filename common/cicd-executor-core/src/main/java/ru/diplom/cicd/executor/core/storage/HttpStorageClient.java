package ru.diplom.cicd.executor.core.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

/**
 * Предварительный HTTP adapter для будущего REST API storage-service.
 *
 * <p>Это намеренно минимальная MVP-реализация без Spring WebClient и без финализации REST-контракта.
 * Когда {@code storage-service} получит полноценный upload/download API, этот adapter можно спокойно
 * расширить: добавить streaming contract, server-side metadata response, retries и auth через
 * {@code secret_ref/credentials_ref}.
 */
public final class HttpStorageClient implements StorageClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpStorageTransport transport;
    private final URI baseUri;
    private final Duration requestTimeout;

    public HttpStorageClient(URI baseUri) {
        this(HttpClient.newHttpClient(), baseUri, DEFAULT_TIMEOUT);
    }

    public HttpStorageClient(URI baseUri, Duration requestTimeout) {
        this(HttpClient.newHttpClient(), baseUri, requestTimeout);
    }

    public HttpStorageClient(HttpClient httpClient, URI baseUri, Duration requestTimeout) {
        this(new JdkHttpStorageTransport(httpClient), baseUri, requestTimeout);
    }

    HttpStorageClient(HttpStorageTransport transport, URI baseUri, Duration requestTimeout) {
        this.transport = Objects.requireNonNull(transport, "Не задан HTTP transport для storage");
        this.baseUri = normalizeBaseUri(baseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "Не задан timeout HTTP storage request");
    }

    @Override
    public CompletionStage<ArtifactDescriptor> upload(StorageUploadRequest request) {
        Objects.requireNonNull(request, "Не задан запрос upload артефакта");
        try {
            if (!Files.isRegularFile(request.sourcePath())) {
                throw new StorageClientException("Исходный артефакт не найден: " + request.sourcePath());
            }

            String namespacePath = StorageUris.normalizeNamespacePath(request.destinationPath());
            String storageUri = StorageUris.toStorageUri(namespacePath);
            String checksum = LocalStorageClient.sha256(request.sourcePath());
            long sizeBytes = Files.size(request.sourcePath());

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", request.contentType());
            headers.put("X-CICD-Artifact-Type", valueOrEmpty(request.artifactType()));
            headers.put("X-CICD-Artifact-Name", request.name());
            headers.put("X-CICD-Checksum-Sha256", checksum);

            return transport
                    .upload(artifactEndpoint(namespacePath), request.sourcePath(), headers, requestTimeout)
                    .thenApply(statusCode -> {
                        ensureSuccess(statusCode, "upload", storageUri);
                        return new ArtifactDescriptor(
                                stableArtifactId(storageUri),
                                request.artifactType(),
                                request.name(),
                                storageUri,
                                request.contentType(),
                                sizeBytes,
                                checksum,
                                request.metadata());
                    });
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(
                    new StorageClientException("Не удалось подготовить upload артефакта", exception));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Path> download(StorageDownloadRequest request) {
        Objects.requireNonNull(request, "Не задан запрос download артефакта");
        try {
            String namespacePath = StorageUris.namespacePath(request.uri());
            Path targetPath = request.targetPath().toAbsolutePath().normalize();
            Path targetParent = targetPath.getParent();
            if (targetParent != null) {
                Files.createDirectories(targetParent);
            }

            return transport
                    .download(artifactEndpoint(namespacePath), targetPath, requestTimeout)
                    .thenApply(statusCode -> {
                        ensureSuccess(statusCode, "download", request.uri());
                        return targetPath;
                    });
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(
                    new StorageClientException("Не удалось подготовить download артефакта", exception));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private URI artifactEndpoint(String namespacePath) {
        return baseUri.resolve("artifacts/" + encodeNamespace(namespacePath));
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "Не задан base URI storage-service");
        String value = baseUri.toString();
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return URI.create(value);
    }

    private static String encodeNamespace(String namespacePath) {
        try (Stream<String> segments = Stream.of(namespacePath.split("/"))) {
            return segments.map(HttpStorageClient::encodeSegment).collect(Collectors.joining("/"));
        }
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void ensureSuccess(int statusCode, String operation, String storageUri) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new CompletionException(new StorageClientException(
                    "Storage service вернул HTTP " + statusCode + " для операции " + operation + ": " + storageUri));
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static UUID stableArtifactId(String storageUri) {
        return UUID.nameUUIDFromBytes(storageUri.getBytes(StandardCharsets.UTF_8));
    }

    interface HttpStorageTransport {

        CompletionStage<Integer> upload(URI uri, Path sourcePath, Map<String, String> headers, Duration timeout);

        CompletionStage<Integer> download(URI uri, Path targetPath, Duration timeout);
    }

    private static final class JdkHttpStorageTransport implements HttpStorageTransport {

        private final HttpClient httpClient;

        private JdkHttpStorageTransport(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "Не задан HTTP client для storage");
        }

        @Override
        public CompletionStage<Integer> upload(
                URI uri, Path sourcePath, Map<String, String> headers, Duration timeout) {
            try {
                HttpRequest.Builder builder =
                        HttpRequest.newBuilder(uri).timeout(timeout).PUT(HttpRequest.BodyPublishers.ofFile(sourcePath));
                headers.forEach(builder::header);

                return httpClient
                        .sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                        .thenApply(HttpResponse::statusCode);
            } catch (FileNotFoundException exception) {
                return CompletableFuture.failedFuture(
                        new StorageClientException("Исходный артефакт не найден: " + sourcePath, exception));
            }
        }

        @Override
        public CompletionStage<Integer> download(URI uri, Path targetPath, Duration timeout) {
            HttpRequest request =
                    HttpRequest.newBuilder(uri).timeout(timeout).GET().build();

            return httpClient
                    .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofFile(
                                    targetPath,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE))
                    .thenApply(HttpResponse::statusCode);
        }
    }
}
