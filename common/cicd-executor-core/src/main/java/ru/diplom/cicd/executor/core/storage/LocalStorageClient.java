package ru.diplom.cicd.executor.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

/**
 * Локальный filesystem adapter для demo, тестов и single-node окружения.
 *
 * <p>Adapter использует namespace внутри заданного root и не позволяет {@code ../} или абсолютным
 * путям выйти за пределы storage root.
 */
public final class LocalStorageClient implements StorageClient {

    private final Path root;

    public LocalStorageClient(Path root) {
        this.root = Objects.requireNonNull(root, "Не задан root локального storage")
                .toAbsolutePath()
                .normalize();
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
            Path targetPath = resolve(namespacePath);
            Files.createDirectories(targetPath.getParent());

            String sourceChecksum = sha256(request.sourcePath());
            if (Files.exists(targetPath)) {
                ensureExistingArtifactIsSame(targetPath, sourceChecksum, storageUri);
            } else {
                Files.copy(request.sourcePath(), targetPath, StandardCopyOption.COPY_ATTRIBUTES);
            }

            return CompletableFuture.completedFuture(new ArtifactDescriptor(
                    stableArtifactId(storageUri),
                    request.artifactType(),
                    request.name(),
                    storageUri,
                    request.contentType(),
                    Files.size(targetPath),
                    sourceChecksum,
                    request.metadata()));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(
                    new StorageClientException("Не удалось сохранить артефакт в local storage", exception));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Path> download(StorageDownloadRequest request) {
        Objects.requireNonNull(request, "Не задан запрос download артефакта");
        try {
            String namespacePath = StorageUris.namespacePath(request.uri());
            Path sourcePath = resolve(namespacePath);
            if (!Files.isRegularFile(sourcePath)) {
                throw new StorageClientException("Артефакт не найден в local storage: " + request.uri());
            }

            Path targetPath = request.targetPath().toAbsolutePath().normalize();
            Path targetParent = targetPath.getParent();
            if (targetParent != null) {
                Files.createDirectories(targetParent);
            }
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return CompletableFuture.completedFuture(targetPath);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(
                    new StorageClientException("Не удалось скачать артефакт из local storage", exception));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private Path resolve(String namespacePath) {
        Path resolvedPath = root.resolve(namespacePath).normalize();
        if (!resolvedPath.startsWith(root)) {
            throw new StorageClientException("Storage namespace выходит за пределы root: " + namespacePath);
        }
        return resolvedPath;
    }

    private void ensureExistingArtifactIsSame(Path targetPath, String sourceChecksum, String storageUri)
            throws IOException {
        String targetChecksum = sha256(targetPath);
        if (!sourceChecksum.equals(targetChecksum)) {
            throw new StorageClientException("Артефакт уже существует с другим содержимым: " + storageUri);
        }
    }

    private static UUID stableArtifactId(String storageUri) {
        return UUID.nameUUIDFromBytes(storageUri.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String sha256(Path path) throws IOException {
        return StorageChecksums.sha256(path);
    }
}
