package ru.diplom.fuzzingcicd.storage.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemStorageClientTest {

    @TempDir
    private Path tempDir;

    private final LocalFilesystemStorageClient storageClient = new LocalFilesystemStorageClient();

    @Test
    void uploadsDownloadsAndDeletesLocalArtifact() throws Exception {
        Path source = tempDir.resolve("source.txt");
        Path stored = tempDir.resolve("objects/source.txt");
        Path downloaded = tempDir.resolve("download/source.txt");
        Files.writeString(source, "artifact-body");

        StoredArtifact artifact = storageClient.upload(source, stored.toUri(), Map.of("kind", "log"));

        assertEquals(stored.toUri(), artifact.uri());
        assertEquals(13, artifact.sizeBytes());
        assertEquals("log", artifact.metadata().get("kind"));

        storageClient.download(artifact.uri(), downloaded);

        assertEquals("artifact-body", Files.readString(downloaded));
        assertTrue(storageClient.delete(artifact.uri()));
        assertFalse(Files.exists(stored));
    }
}
