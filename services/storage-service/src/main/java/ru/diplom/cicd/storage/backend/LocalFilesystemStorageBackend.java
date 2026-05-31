package ru.diplom.cicd.storage.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.executor.core.storage.StorageUris;

/**
 * Локальный filesystem backend storage-service.
 *
 * <p>Backend хранит файлы только внутри настроенного root, а повторная запись в тот же
 * {@code storage://} URI считается идемпотентной только при совпадении sha256.
 */
@Component
public final class LocalFilesystemStorageBackend {

    private final Path root;

    @Autowired
    public LocalFilesystemStorageBackend(
            @Value("${cicd.storage.local.root:${java.io.tmpdir}/cicd-storage-service}") String root) {
        this(Path.of(root));
    }

    public LocalFilesystemStorageBackend(Path root) {
        this.root = Objects.requireNonNull(root, "Не задан root локального storage")
                .toAbsolutePath()
                .normalize();
    }

    public ArtifactDescriptor save(Path sourcePath, StorageSaveRequest request) {
        Objects.requireNonNull(sourcePath, "Не задан исходный файл storage artifact");
        Objects.requireNonNull(request, "Не задан запрос сохранения storage artifact");

        try {
            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedSource)) {
                throw new StorageClientException("Исходный файл для storage не найден: " + sourcePath);
            }

            String namespacePath = StorageUris.normalizeNamespacePath(request.destinationPath());
            String storageUri = StorageUris.toStorageUri(namespacePath);
            Path targetPath = resolve(namespacePath);
            Files.createDirectories(targetPath.getParent());

            String sourceChecksum = StorageChecksums.sha256(normalizedSource);
            verifyExpectedChecksum(sourceChecksum, request.expectedChecksumSha256(), storageUri);
            if (Files.exists(targetPath)) {
                ensureExistingArtifactIsSame(targetPath, sourceChecksum, storageUri);
            } else {
                Files.copy(normalizedSource, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
            }

            return new ArtifactDescriptor(
                    stableArtifactId(storageUri),
                    request.artifactType(),
                    request.name(),
                    storageUri,
                    request.contentType(),
                    Files.size(targetPath),
                    sourceChecksum,
                    request.metadata());
        } catch (IOException exception) {
            throw new StorageClientException("Не удалось сохранить файл в local storage", exception);
        }
    }

    public Path load(String namespacePath) {
        String normalizedNamespacePath = StorageUris.normalizeNamespacePath(namespacePath);
        Path artifactPath = resolve(normalizedNamespacePath);
        if (!Files.isRegularFile(artifactPath)) {
            throw new StorageClientException(
                    "Артефакт не найден: " + StorageUris.toStorageUri(normalizedNamespacePath));
        }
        return artifactPath;
    }

    Path resolve(String namespacePath) {
        Path resolvedPath = root.resolve(namespacePath).normalize();
        if (!resolvedPath.startsWith(root)) {
            throw new StorageClientException("Storage namespace выходит за пределы root: " + namespacePath);
        }
        return resolvedPath;
    }

    private static void verifyExpectedChecksum(String actualChecksum, String expectedChecksum, String storageUri) {
        if (expectedChecksum != null && !expectedChecksum.equals(actualChecksum)) {
            throw new StorageChecksumMismatchException(
                    "SHA-256 checksum артефакта не совпадает для %s: expected=%s, actual=%s"
                            .formatted(storageUri, expectedChecksum, actualChecksum));
        }
    }

    private void ensureExistingArtifactIsSame(Path targetPath, String sourceChecksum, String storageUri)
            throws IOException {
        String targetChecksum = StorageChecksums.sha256(targetPath);
        if (!sourceChecksum.equals(targetChecksum)) {
            throw new StorageClientException("Артефакт уже существует с другим содержимым: " + storageUri);
        }
    }

    private static UUID stableArtifactId(String storageUri) {
        return UUID.nameUUIDFromBytes(storageUri.getBytes(StandardCharsets.UTF_8));
    }
}
