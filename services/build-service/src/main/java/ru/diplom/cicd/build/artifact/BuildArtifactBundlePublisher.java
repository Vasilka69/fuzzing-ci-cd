package ru.diplom.cicd.build.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;

/**
 * Публикует build outputs одним bundle-архивом. В архив попадают файлы, найденные
 * по expected_artifacts, и manifest с относительными путями; это сохраняет downstream
 * контракт простым и не заставляет следующие executor-ы собирать набор URI вручную.
 */
@SuppressWarnings("java:S1192")
@Component
public final class BuildArtifactBundlePublisher {

    public static final String ARTIFACT_TYPE = "build_artifacts";
    public static final String ARCHIVE_FILE_NAME = "build-artifacts.tar.gz";
    public static final String ARCHIVE_FORMAT = "tar.gz";
    public static final String CONTENT_TYPE = "application/gzip";

    private static final String TAR_EXECUTABLE = "tar";
    private static final String WORK_DIR = ".build-artifacts";
    private static final String STAGING_DIR = "staging";
    private static final String ARCHIVE_ARTIFACTS_DIR = "artifacts";
    private static final String MANIFEST_FILE_NAME = "artifact-manifest.json";
    private static final int ERROR_DETAILS_LIMIT = 1000;

    private final StorageClient storageClient;
    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper;

    public BuildArtifactBundlePublisher(
            StorageClient storageClient, ProcessRunner processRunner, ObjectMapper objectMapper) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public BuildArtifactBundle publish(
            JobMessage job,
            Path workspaceRoot,
            Path workingDirectory,
            List<String> patterns,
            List<ExpectedArtifact> artifacts) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }

        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path workDir = root.resolve(WORK_DIR).normalize();
        Path stagingRoot = workDir.resolve(STAGING_DIR).normalize();
        Path archivePath = workDir.resolve(ARCHIVE_FILE_NAME).normalize();
        ensureInsideWorkspace(root, workDir);
        ensureInsideWorkspace(root, stagingRoot);
        ensureInsideWorkspace(root, archivePath);

        prepareStaging(workingDirectory.toAbsolutePath().normalize(), stagingRoot, patterns, artifacts, job);
        createArchive(stagingRoot, archivePath, job.timeoutSeconds());
        ArtifactDescriptor descriptor = upload(job, archivePath, artifacts);
        Map<String, Object> metadata = bundleMetadata(descriptor, artifacts);
        return new BuildArtifactBundle(descriptor, artifacts, metadata, logs(descriptor, artifacts));
    }

    private void prepareStaging(
            Path workingDirectory,
            Path stagingRoot,
            List<String> patterns,
            List<ExpectedArtifact> artifacts,
            JobMessage job) {
        Path artifactsRoot = stagingRoot.resolve(ARCHIVE_ARTIFACTS_DIR).normalize();
        try {
            Files.createDirectories(artifactsRoot);
            for (ExpectedArtifact artifact : artifacts) {
                copyArtifact(workingDirectory, artifactsRoot, artifact);
            }
            writeManifest(stagingRoot, patterns, artifacts, job);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.artifacts.bundle-staging",
                    "Не удалось подготовить build artifacts bundle",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void copyArtifact(Path workingDirectory, Path artifactsRoot, ExpectedArtifact artifact) throws IOException {
        Path relativePath = artifact.relativePath().normalize();
        if (relativePath.isAbsolute() || startsWithParentTraversal(relativePath)) {
            throw ExecutorJobException.validation("expected artifact выходит за пределы рабочей директории");
        }
        Path sourcePath = workingDirectory.resolve(relativePath).normalize();
        if (!sourcePath.startsWith(workingDirectory) || !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            throw ExecutorJobException.validation("expected artifact не найден в рабочей директории");
        }
        Path targetPath = artifactsRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(artifactsRoot)) {
            throw ExecutorJobException.validation("expected artifact выходит за пределы build artifacts bundle");
        }
        Path targetParent = targetPath.getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean startsWithParentTraversal(Path relativePath) {
        return "..".equals(relativePath.toString()) || relativePath.startsWith("..");
    }

    private void writeManifest(
            Path stagingRoot, List<String> patterns, List<ExpectedArtifact> artifacts, JobMessage job)
            throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("artifactType", ARTIFACT_TYPE);
        manifest.put("archiveFormat", ARCHIVE_FORMAT);
        manifest.put("jobExecutionId", job.jobExecutionId().toString());
        manifest.put("expectedArtifactPatterns", patterns == null ? List.of() : patterns);
        manifest.put("files", artifacts.stream().map(this::manifestFile).toList());
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(stagingRoot.resolve(MANIFEST_FILE_NAME).toFile(), manifest);
    }

    private Map<String, Object> manifestFile(ExpectedArtifact artifact) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("pattern", artifact.pattern());
        file.put("path", artifact.relativePathText());
        file.put("archivePath", ARCHIVE_ARTIFACTS_DIR + "/" + artifact.relativePathText());
        file.put("sizeBytes", artifact.sizeBytes());
        return file;
    }

    private void createArchive(Path stagingRoot, Path archivePath, long timeoutSeconds) {
        ProcessExecutionResult result = runTar(stagingRoot, archivePath, timeoutSeconds);
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "build.artifacts.archive.timeout",
                    "Архивация build artifacts превысила timeout job",
                    null,
                    Map.of("timeoutSeconds", timeoutSeconds),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.artifacts.archive.failed",
                    "tar завершился с ошибкой при архивации build artifacts",
                    errorDetails(result),
                    Map.of("exitCode", result.exitCode()),
                    ExecutionStatus.FAILED);
        }
        if (!Files.isRegularFile(archivePath)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.artifacts.archive.missing",
                    "tar завершился успешно, но build artifacts bundle не найден",
                    null,
                    Map.of("archivePath", archivePath.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private ProcessExecutionResult runTar(Path stagingRoot, Path archivePath, long timeoutSeconds) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(List.of(
                            TAR_EXECUTABLE,
                            "--sort=name",
                            "--mtime=@0",
                            "--owner=0",
                            "--group=0",
                            "--numeric-owner",
                            "-czf",
                            archivePath.toString(),
                            "-C",
                            stagingRoot.toString(),
                            "."))
                    .workingDirectory(stagingRoot)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.artifacts.archive.process-runner",
                    "Не удалось запустить tar через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private ArtifactDescriptor upload(JobMessage job, Path archivePath, List<ExpectedArtifact> artifacts) {
        try {
            return storageClient
                    .upload(new StorageUploadRequest(
                            archivePath,
                            "build-artifacts/%s/%s".formatted(job.jobExecutionId(), ARCHIVE_FILE_NAME),
                            ARTIFACT_TYPE,
                            ARCHIVE_FILE_NAME,
                            CONTENT_TYPE,
                            Map.of(
                                    "jobExecutionId",
                                    job.jobExecutionId().toString(),
                                    "artifactCount",
                                    artifacts.size(),
                                    "archiveFormat",
                                    ARCHIVE_FORMAT,
                                    "manifestPath",
                                    MANIFEST_FILE_NAME)))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageUploadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageUploadException(exception);
        }
    }

    private ExecutorJobException storageUploadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "build.artifacts.upload-failed",
                "Не удалось загрузить build artifacts bundle во внутреннее хранилище",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private Map<String, Object> bundleMetadata(ArtifactDescriptor descriptor, List<ExpectedArtifact> artifacts) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("uri", descriptor.uri());
        metadata.put("fileName", ARCHIVE_FILE_NAME);
        metadata.put("format", ARCHIVE_FORMAT);
        metadata.put("contentType", CONTENT_TYPE);
        metadata.put("artifactCount", artifacts.size());
        metadata.put("manifestPath", MANIFEST_FILE_NAME);
        metadata.put("sizeBytes", descriptor.sizeBytes());
        metadata.put("checksumSha256", descriptor.checksumSha256());
        return metadata;
    }

    private String logs(ArtifactDescriptor descriptor, List<ExpectedArtifact> artifacts) {
        return "Build artifacts tar.gz опубликован: %s (%d файлов)".formatted(descriptor.uri(), artifacts.size());
    }

    private void ensureInsideWorkspace(Path workspaceRoot, Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw new ExecutorJobException(
                    ErrorType.SECURITY_ERROR,
                    "build.artifacts.workspace-escape",
                    "Build artifacts bundle выходит за пределы workspace",
                    null,
                    Map.of("path", path.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }
}
