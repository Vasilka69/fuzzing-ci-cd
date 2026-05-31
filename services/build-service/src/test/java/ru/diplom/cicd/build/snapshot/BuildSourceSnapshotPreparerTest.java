package ru.diplom.cicd.build.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.build.runner.BuildParameters;
import ru.diplom.cicd.build.runner.BuildTool;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;

class BuildSourceSnapshotPreparerTest {

    private static final String SOURCE_SNAPSHOT_URI = "storage://source-snapshots/job-1/source-snapshot.tar.gz";

    @TempDir
    private Path tempDir;

    @Test
    void prepareDownloadsAndExtractsSourceSnapshot() throws Exception {
        LocalStorageClient storageClient = storageClientWithSourceSnapshot();
        BuildSourceSnapshotPreparer preparer = new BuildSourceSnapshotPreparer(storageClient, new LocalProcessRunner());
        BuildParameters parameters = parameters();

        SourceSnapshotWorkspace workspace = preparer.prepare(parameters, tempDir.resolve("workspace"), 10);

        assertTrue(Files.isRegularFile(workspace.sourceRoot().resolve("README.md")));
        assertTrue(Files.isRegularFile(workspace.archivePath()));
        assertTrue(workspace.logs().contains("Source snapshot скачан из storage"));
        assertEquals(
                "hello",
                Files.readString(workspace.sourceRoot().resolve("README.md")).trim());
    }

    @Test
    void prepareRejectsArchiveEntryOutsideWorkspace() {
        BuildSourceSnapshotPreparer preparer = new BuildSourceSnapshotPreparer(
                new WritingStorageClient(), new StubProcessRunner(processResult(0, "../evil.txt\n", "")));
        BuildParameters parameters = parameters();

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> preparer.prepare(parameters, tempDir, 10));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }

    @Test
    void prepareRejectsArchiveSymlinkEntries() {
        ProcessExecutionResult validList = processResult(0, "./link\n", "");
        ProcessExecutionResult symlinkMetadata =
                processResult(0, "lrwxrwxrwx root/root 0 2026-05-30 09:00 ./link -> /etc/passwd\n", "");
        BuildSourceSnapshotPreparer preparer = new BuildSourceSnapshotPreparer(
                new WritingStorageClient(), new SequenceProcessRunner(List.of(validList, symlinkMetadata)));
        BuildParameters parameters = parameters();

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> preparer.prepare(parameters, tempDir, 10));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }

    private BuildParameters parameters() {
        return new BuildParameters(
                BuildTool.MAVEN, SOURCE_SNAPSHOT_URI, Path.of("."), "./mvnw", List.of("test"), Map.of());
    }

    private LocalStorageClient storageClientWithSourceSnapshot() throws Exception {
        Path sourceDirectory = tempDir.resolve("source-project");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("README.md"), "hello\n");
        Path archivePath = tempDir.resolve("source-snapshot.tar.gz");
        tar(sourceDirectory, "-czf", archivePath.toString(), "-C", sourceDirectory.toString(), ".");

        LocalStorageClient storageClient = new LocalStorageClient(tempDir.resolve("storage"));
        storageClient
                .upload(new StorageUploadRequest(
                        archivePath,
                        "source-snapshots/job-1/source-snapshot.tar.gz",
                        "source_snapshot",
                        "source-snapshot.tar.gz",
                        "application/gzip",
                        Map.of()))
                .toCompletableFuture()
                .join();
        return storageClient;
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
            throw new AssertionError("Tar test command failed: " + command + System.lineSeparator() + stderr);
        }
    }

    private static ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(
                        processChunk(ProcessStreamType.STDOUT, 0, stdout),
                        processChunk(ProcessStreamType.STDERR, 1, stderr)));
    }

    private static ProcessOutputChunk processChunk(ProcessStreamType stream, long sequence, String text) {
        return new ProcessOutputChunk(stream, sequence, text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class WritingStorageClient implements StorageClient {

        @Override
        public CompletionStage<ArtifactDescriptor> upload(StorageUploadRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("upload не используется в тесте"));
        }

        @Override
        public CompletionStage<Path> download(StorageDownloadRequest request) {
            try {
                Files.createDirectories(request.targetPath().getParent());
                Files.writeString(request.targetPath(), "fake archive");
                return CompletableFuture.completedFuture(request.targetPath());
            } catch (java.io.IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
    }

    private record StubProcessRunner(ProcessExecutionResult result) implements ProcessRunner {

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            return result;
        }
    }

    private static final class SequenceProcessRunner implements ProcessRunner {

        private final List<ProcessExecutionResult> results;
        private int index;

        private SequenceProcessRunner(List<ProcessExecutionResult> results) {
            this.results = List.copyOf(results);
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            ProcessExecutionResult result = results.get(index);
            index++;
            return result;
        }
    }
}
