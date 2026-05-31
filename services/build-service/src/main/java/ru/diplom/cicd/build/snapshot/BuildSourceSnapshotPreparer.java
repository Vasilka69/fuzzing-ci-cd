package ru.diplom.cicd.build.snapshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.build.runner.BuildParameters;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;

/**
 * Готовит source snapshot для build job: скачивает tar.gz из internal storage и распаковывает его
 * в каталог, из которого затем запускается whitelist build entrypoint.
 */
@SuppressWarnings("java:S1192")
@Component
public final class BuildSourceSnapshotPreparer {

    private static final String TAR_EXECUTABLE = "tar";
    private static final String SNAPSHOT_ARCHIVE = "source-snapshot.tar.gz";
    private static final String SNAPSHOT_WORK_DIR = ".build-source";
    private static final String SOURCE_DIR = "source";
    private static final int ERROR_DETAILS_LIMIT = 1000;

    private final StorageClient storageClient;
    private final ProcessRunner processRunner;

    public BuildSourceSnapshotPreparer(StorageClient storageClient, ProcessRunner processRunner) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public SourceSnapshotWorkspace prepare(BuildParameters parameters, Path workspaceRoot, long timeoutSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");

        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path snapshotWorkDir = root.resolve(SNAPSHOT_WORK_DIR).normalize();
        Path archivePath = snapshotWorkDir.resolve(SNAPSHOT_ARCHIVE).normalize();
        Path sourceRoot = root.resolve(SOURCE_DIR).normalize();
        ensureInsideWorkspace(root, archivePath);
        ensureInsideWorkspace(root, sourceRoot);

        prepareDirectories(snapshotWorkDir, sourceRoot);
        downloadSnapshot(parameters.sourceSnapshotUri(), archivePath);
        validateArchiveEntries(archivePath, sourceRoot, timeoutSeconds);
        extractArchive(archivePath, sourceRoot, timeoutSeconds);
        validateExtractedSymlinks(sourceRoot);

        return new SourceSnapshotWorkspace(
                sourceRoot, archivePath, logs(parameters.sourceSnapshotUri(), sourceRoot, archivePath));
    }

    private void prepareDirectories(Path snapshotWorkDir, Path sourceRoot) {
        try {
            Files.createDirectories(snapshotWorkDir);
            Files.createDirectories(sourceRoot);
        } catch (java.io.IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.snapshot.workspace",
                    "Не удалось подготовить workspace для source snapshot",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void downloadSnapshot(String sourceSnapshotUri, Path archivePath) {
        try {
            storageClient
                    .download(new StorageDownloadRequest(sourceSnapshotUri, archivePath))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageDownloadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageDownloadException(exception);
        }
    }

    private ExecutorJobException storageDownloadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "build.snapshot.download-failed",
                "Не удалось скачать source snapshot из внутреннего хранилища",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private void validateArchiveEntries(Path archivePath, Path sourceRoot, long timeoutSeconds) {
        ProcessExecutionResult result = runTar(
                List.of(TAR_EXECUTABLE, "-tzf", archivePath.toString()),
                sourceRoot,
                Duration.ofSeconds(timeoutSeconds),
                "build.snapshot.tar-list");
        if (result.timedOut()) {
            throw snapshotTimeout(timeoutSeconds, "build.snapshot.tar-list.timeout");
        }
        if (result.exitCode() != 0) {
            throw invalidArchive("tar не смог прочитать source snapshot", result);
        }

        String stdout = result.stdoutText(StandardCharsets.UTF_8);
        if (StringUtils.isBlank(stdout)) {
            throw ExecutorJobException.validation("source snapshot не содержит файлов для сборки");
        }
        stdout.lines().forEach(this::validateArchiveEntry);
        validateArchiveEntryTypes(archivePath, sourceRoot, timeoutSeconds);
    }

    private void validateArchiveEntry(String rawEntry) {
        String entry = StringUtils.trimToEmpty(rawEntry).replace('\\', '/');
        if (StringUtils.isBlank(entry)) {
            return;
        }
        Path entryPath = Path.of(entry).normalize();
        if (entryPath.isAbsolute()
                || entry.startsWith("/")
                || "..".equals(entryPath.toString())
                || entryPath.startsWith("..")) {
            throw ExecutorJobException.validation("source snapshot содержит путь за пределы workspace: " + rawEntry);
        }
    }

    private void validateArchiveEntryTypes(Path archivePath, Path sourceRoot, long timeoutSeconds) {
        ProcessExecutionResult result = runTar(
                List.of(TAR_EXECUTABLE, "-tvzf", archivePath.toString()),
                sourceRoot,
                Duration.ofSeconds(timeoutSeconds),
                "build.snapshot.tar-list");
        if (result.timedOut()) {
            throw snapshotTimeout(timeoutSeconds, "build.snapshot.tar-list.timeout");
        }
        if (result.exitCode() != 0) {
            throw invalidArchive("tar не смог прочитать metadata source snapshot", result);
        }
        result.stdoutText(StandardCharsets.UTF_8).lines().forEach(this::validateArchiveEntryType);
    }

    private void validateArchiveEntryType(String rawLine) {
        String line = StringUtils.trimToEmpty(rawLine);
        if (StringUtils.isBlank(line)) {
            return;
        }
        char type = line.charAt(0);
        if (type != '-' && type != 'd') {
            throw ExecutorJobException.validation(
                    "source snapshot содержит неподдерживаемый тип tar entry: " + rawLine);
        }
    }

    private void extractArchive(Path archivePath, Path sourceRoot, long timeoutSeconds) {
        ProcessExecutionResult result = runTar(
                List.of(TAR_EXECUTABLE, "-xzf", archivePath.toString(), "-C", sourceRoot.toString()),
                sourceRoot,
                Duration.ofSeconds(timeoutSeconds),
                "build.snapshot.extract");
        if (result.timedOut()) {
            throw snapshotTimeout(timeoutSeconds, "build.snapshot.extract.timeout");
        }
        if (result.exitCode() != 0) {
            throw invalidArchive("tar не смог распаковать source snapshot", result);
        }
    }

    private ProcessExecutionResult runTar(
            List<String> command, Path workingDirectory, Duration timeout, String errorCode) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(command)
                    .workingDirectory(workingDirectory)
                    .timeout(timeout)
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    errorCode,
                    "Не удалось запустить tar через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void validateExtractedSymlinks(Path sourceRoot) {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isSymbolicLink).forEach(path -> validateSymlink(sourceRoot, path));
        } catch (java.io.IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.snapshot.symlink-validation",
                    "Не удалось проверить распакованный source snapshot",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void validateSymlink(Path sourceRoot, Path symlink) {
        try {
            Path target = Files.readSymbolicLink(symlink);
            Path resolvedTarget = target.isAbsolute()
                    ? target.normalize()
                    : symlink.getParent().resolve(target).normalize();
            if (!resolvedTarget.startsWith(sourceRoot)) {
                throw ExecutorJobException.validation(
                        "source snapshot содержит symlink за пределы workspace: " + sourceRoot.relativize(symlink));
            }
        } catch (java.io.IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.snapshot.symlink-validation",
                    "Не удалось прочитать symlink из source snapshot",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private ExecutorJobException invalidArchive(String message, ProcessExecutionResult result) {
        return new ExecutorJobException(
                ErrorType.VALIDATION_ERROR,
                "build.snapshot.invalid-archive",
                message,
                errorDetails(result),
                Map.of("exitCode", result.exitCode()),
                ExecutionStatus.FAILED);
    }

    private ExecutorJobException snapshotTimeout(long timeoutSeconds, String code) {
        return new ExecutorJobException(
                ErrorType.TIMEOUT,
                code,
                "Подготовка source snapshot превысила timeout job",
                null,
                Map.of("timeoutSeconds", timeoutSeconds),
                ExecutionStatus.TIMEOUT);
    }

    private void ensureInsideWorkspace(Path workspaceRoot, Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw ExecutorJobException.validation("source snapshot path выходит за пределы workspace");
        }
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }

    private String logs(String sourceSnapshotUri, Path sourceRoot, Path archivePath) {
        return """
                Source snapshot скачан из storage: %s
                Source snapshot tar.gz распакован: %s
                Архив source snapshot сохранен во временном workspace: %s""".formatted(sourceSnapshotUri, sourceRoot.getFileName(), archivePath.getFileName())
                .stripTrailing();
    }
}
