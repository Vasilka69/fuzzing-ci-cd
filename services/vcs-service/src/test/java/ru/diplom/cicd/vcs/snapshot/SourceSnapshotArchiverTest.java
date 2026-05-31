package ru.diplom.cicd.vcs.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;

class SourceSnapshotArchiverTest {

    @TempDir
    private Path tempDir;

    @Test
    void createsTarGzSnapshotWithMetadataAndReadableContent() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("README.md"), "hello\n");
        Files.writeString(source.resolve("src/App.java"), "class App {}\n");
        Path archivePath = tempDir.resolve("source-snapshot.tar.gz");
        SourceSnapshotArchiver archiver = new SourceSnapshotArchiver(new LocalProcessRunner());

        SourceSnapshotArchive archive = archiver.create(source, archivePath, tempDir, 30);

        assertEquals(archivePath.toAbsolutePath().normalize(), archive.path());
        assertEquals("source-snapshot.tar.gz", archive.relativePath());
        assertEquals("source-snapshot.tar.gz", archive.fileName());
        assertEquals("tar.gz", archive.format());
        assertTrue(archive.sizeBytes() > 0);
        assertTrue(archive.checksumSha256().matches("[0-9a-f]{64}"));
        assertTrue(archive.logs().contains("Source snapshot tar.gz подготовлен"));

        Path unpacked = tempDir.resolve("unpacked");
        Files.createDirectories(unpacked);
        tar(unpacked, "-xzf", archivePath.toString(), "-C", unpacked.toString());
        assertEquals("hello\n", Files.readString(unpacked.resolve("README.md")));
        assertEquals("class App {}\n", Files.readString(unpacked.resolve("src/App.java")));
    }

    private void tar(Path workingDirectory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("tar");
        command.addAll(List.of(args));
        Process process =
                new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("tar test command failed: " + command + System.lineSeparator() + stderr);
        }
    }
}
