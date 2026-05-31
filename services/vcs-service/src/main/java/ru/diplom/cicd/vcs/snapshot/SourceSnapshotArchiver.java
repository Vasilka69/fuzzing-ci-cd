package ru.diplom.cicd.vcs.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;

/**
 * Создает source snapshot через системный {@code tar}, запущенный общим process runner-ом.
 */
@Component
public final class SourceSnapshotArchiver {

    public static final String FORMAT = "tar.gz";

    private static final String TAR_EXECUTABLE = "tar";
    private static final int ERROR_DETAILS_LIMIT = 1000;

    public static final String ARCHIVE_PATH_ARGUMENT = "archivePath";
    public static final String SOURCE_DIRECTORY_ARGUMENT = "sourceDirectory";
    public static final String WORKSPACE_ROOT_ARGUMENT = "workspaceRoot";

    private final ProcessRunner processRunner;

    public SourceSnapshotArchiver(ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public SourceSnapshotArchive create(
            Path sourceDirectory, Path archivePath, Path workspaceRoot, long timeoutSeconds) {
        Objects.requireNonNull(sourceDirectory, SOURCE_DIRECTORY_ARGUMENT);
        Objects.requireNonNull(archivePath, ARCHIVE_PATH_ARGUMENT);
        Objects.requireNonNull(workspaceRoot, WORKSPACE_ROOT_ARGUMENT);

        Path normalizedSourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        Path normalizedArchivePath = archivePath.toAbsolutePath().normalize();
        Path normalizedWorkspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        ensureSourceDirectoryExists(normalizedSourceDirectory);
        prepareArchiveParent(normalizedArchivePath);

        ProcessExecutionResult result = runTar(normalizedSourceDirectory, normalizedArchivePath, timeoutSeconds);
        ensureArchiveCreated(normalizedArchivePath);

        try {
            return new SourceSnapshotArchive(
                    normalizedArchivePath,
                    relativePath(normalizedWorkspaceRoot, normalizedArchivePath),
                    fileName(normalizedArchivePath),
                    FORMAT,
                    Files.size(normalizedArchivePath),
                    sha256(normalizedArchivePath),
                    logs(result));
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.snapshot.metadata",
                    "Не удалось прочитать metadata source snapshot",
                    exception.getMessage(),
                    Map.of(ARCHIVE_PATH_ARGUMENT, normalizedArchivePath.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private void ensureSourceDirectoryExists(Path sourceDirectory) {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.snapshot.source-missing",
                    "Каталог source snapshot не найден",
                    null,
                    Map.of(SOURCE_DIRECTORY_ARGUMENT, sourceDirectory.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private void prepareArchiveParent(Path archivePath) {
        Path parent = archivePath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.snapshot.workspace",
                    "Не удалось подготовить каталог для source snapshot",
                    exception.getMessage(),
                    Map.of(ARCHIVE_PATH_ARGUMENT, archivePath.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private ProcessExecutionResult runTar(Path sourceDirectory, Path archivePath, long timeoutSeconds) {
        try {
            ProcessExecutionResult result = processRunner.run(ProcessExecutionRequest.builder(List.of(
                            TAR_EXECUTABLE, "-czf", archivePath.toString(), "-C", sourceDirectory.toString(), "."))
                    .workingDirectory(sourceDirectory)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build());
            if (result.timedOut()) {
                throw new ExecutorJobException(
                        ErrorType.TIMEOUT,
                        "vcs.snapshot.timeout",
                        "Архивация source snapshot превысила timeout job",
                        null,
                        Map.of("timeoutSeconds", timeoutSeconds),
                        ExecutionStatus.TIMEOUT);
            }
            if (result.exitCode() != 0) {
                throw new ExecutorJobException(
                        ErrorType.INFRASTRUCTURE_ERROR,
                        "vcs.snapshot.tar-failed",
                        "tar завершился с ошибкой при архивации source snapshot",
                        errorDetails(result),
                        Map.of("exitCode", result.exitCode()),
                        ExecutionStatus.FAILED);
            }
            return result;
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.snapshot.process-runner",
                    "Не удалось запустить tar через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void ensureArchiveCreated(Path archivePath) {
        if (!Files.isRegularFile(archivePath)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.snapshot.archive-missing",
                    "tar завершился успешно, но source snapshot не найден",
                    null,
                    Map.of(ARCHIVE_PATH_ARGUMENT, archivePath.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private String logs(ProcessExecutionResult result) {
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        StringBuilder logs = new StringBuilder();
        logs.append("Source snapshot tar.gz подготовлен");
        if (StringUtils.isNotBlank(stdout)) {
            logs.append(System.lineSeparator()).append(stdout.stripTrailing());
        }
        if (StringUtils.isNotBlank(stderr)) {
            logs.append(System.lineSeparator()).append(stderr.stripTrailing());
        }
        return logs.toString();
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }

    private String relativePath(Path workspaceRoot, Path archivePath) {
        if (archivePath.startsWith(workspaceRoot)) {
            return workspaceRoot.relativize(archivePath).toString();
        }
        return archivePath.toString();
    }

    private String fileName(Path archivePath) {
        Path fileName = archivePath.getFileName();
        return fileName == null ? "source-snapshot.tar.gz" : fileName.toString();
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = Files.newInputStream(path);
                DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK не поддерживает SHA-256", exception);
        }
    }
}
