package ru.diplom.fuzzingcicd.storage.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class LocalFilesystemStorageClient implements StorageClient {

    @Override
    public StoredArtifact upload(Path source, URI destinationUri, Map<String, String> metadata) {
        try {
            Path destination = resolveLocalPath(destinationUri);
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredArtifact(
                    destination.toUri(),
                    Files.size(destination),
                    StorageChecksum.sha256(destination),
                    metadata
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to upload artifact to " + destinationUri, exception);
        }
    }

    @Override
    public Path download(URI sourceUri, Path destination) {
        try {
            Path source = resolveLocalPath(sourceUri);
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to download artifact from " + sourceUri, exception);
        }
    }

    @Override
    public boolean delete(URI uri) {
        try {
            return Files.deleteIfExists(resolveLocalPath(uri));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete artifact " + uri, exception);
        }
    }

    private static Path resolveLocalPath(URI uri) {
        if (uri.getScheme() == null) {
            return Path.of(uri.toString());
        }
        if ("file".equals(uri.getScheme())) {
            return Path.of(uri);
        }
        throw new IllegalArgumentException("Only file storage URIs are supported by local client: " + uri);
    }
}
