package ru.diplom.fuzzingcicd.storage.client;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

public interface StorageClient {

    StoredArtifact upload(Path source, URI destinationUri, Map<String, String> metadata);

    Path download(URI sourceUri, Path destination);

    boolean delete(URI uri);
}
