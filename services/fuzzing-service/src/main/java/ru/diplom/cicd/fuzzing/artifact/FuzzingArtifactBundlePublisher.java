package ru.diplom.cicd.fuzzing.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
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
import ru.diplom.cicd.fuzzing.runner.FuzzingKernelExecutionResult;

/**
 * Собирает воспроизводимый отчет fuzzing-запуска из AFL++ output и публикует его одним bundle-артефактом.
 * Kafka получает только descriptor и компактную metadata, а crash/hang/corpus файлы остаются в storage.
 */
@SuppressWarnings("java:S1192")
@Component
public final class FuzzingArtifactBundlePublisher {

    public static final String ARTIFACT_TYPE = "fuzzing_report";
    public static final String ARCHIVE_FILE_NAME = "fuzzing-report.tar.gz";
    public static final String ARCHIVE_FORMAT = "tar.gz";
    public static final String CONTENT_TYPE = "application/gzip";
    public static final String REPORT_FILE_NAME = "fuzzing-report.json";

    private static final String TAR_EXECUTABLE = "tar";
    private static final String WORK_DIR = ".fuzzing-artifacts";
    private static final String STAGING_DIR = "staging";
    private static final String ARCHIVE_AFL_OUTPUT_DIR = "afl-output";
    private static final int ERROR_DETAILS_LIMIT = 1000;

    private final StorageClient storageClient;
    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper;

    public FuzzingArtifactBundlePublisher(
            StorageClient storageClient, ProcessRunner processRunner, ObjectMapper objectMapper) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public FuzzingArtifactBundle publish(
            JobMessage job, Path workspaceRoot, FuzzingKernelExecutionResult executionResult) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(executionResult, "executionResult");

        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path aflOutputDirectory =
                executionResult.aflOutputDirectory().toAbsolutePath().normalize();
        ensureInsideWorkspace(root, aflOutputDirectory, "fuzzing.artifacts.afl-output-escape");

        Path workDir = root.resolve(WORK_DIR).normalize();
        Path stagingRoot = workDir.resolve(STAGING_DIR).normalize();
        Path archivePath = workDir.resolve(ARCHIVE_FILE_NAME).normalize();
        ensureInsideWorkspace(root, workDir, "fuzzing.artifacts.workdir-escape");
        ensureInsideWorkspace(root, stagingRoot, "fuzzing.artifacts.staging-escape");
        ensureInsideWorkspace(root, archivePath, "fuzzing.artifacts.archive-escape");

        FuzzingReport report = prepareStaging(stagingRoot, aflOutputDirectory, job, executionResult);
        createArchive(stagingRoot, archivePath, job.timeoutSeconds());
        ArtifactDescriptor descriptor = upload(job, archivePath, report);
        Map<String, Object> metadata = bundleMetadata(descriptor, report);
        return new FuzzingArtifactBundle(descriptor, report, metadata, logs(descriptor, report));
    }

    private FuzzingReport prepareStaging(
            Path stagingRoot, Path aflOutputDirectory, JobMessage job, FuzzingKernelExecutionResult executionResult) {
        try {
            Files.createDirectories(stagingRoot);
            List<CapturedFuzzingFile> capturedFiles = capturedFiles(aflOutputDirectory);
            for (CapturedFuzzingFile capturedFile : capturedFiles) {
                copyCapturedFile(stagingRoot, aflOutputDirectory, capturedFile);
            }
            FuzzingReport report = report(job, executionResult, capturedFiles);
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(stagingRoot.resolve(REPORT_FILE_NAME).toFile(), report);
            return report;
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.artifacts.staging",
                    "Не удалось подготовить fuzzing report bundle",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private List<CapturedFuzzingFile> capturedFiles(Path aflOutputDirectory) throws IOException {
        if (!Files.isDirectory(aflOutputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(aflOutputDirectory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> capturedFile(aflOutputDirectory, path))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(file -> file.relativePath().toString()))
                    .toList();
        }
    }

    private CapturedFuzzingFile capturedFile(Path aflOutputDirectory, Path path) {
        Path relativePath = aflOutputDirectory.relativize(path).normalize();
        String category = category(relativePath);
        if (category == null || isAflReadme(relativePath)) {
            return null;
        }
        try {
            return new CapturedFuzzingFile(category, relativePath, Files.size(path));
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.artifacts.file-stat",
                    "Не удалось прочитать metadata AFL++ artifact",
                    exception.getMessage(),
                    Map.of(
                            "path",
                            relativePath.toString(),
                            "exceptionClass",
                            exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private String category(Path relativePath) {
        for (Path part : relativePath) {
            String value = part.toString();
            if ("crashes".equals(value)) {
                return "crash";
            }
            if ("hangs".equals(value)) {
                return "hang";
            }
            if ("queue".equals(value)) {
                return "corpus";
            }
        }
        return "fuzzer_stats".equals(fileName(relativePath)) ? "stats" : null;
    }

    private boolean isAflReadme(Path relativePath) {
        return "README.txt".equalsIgnoreCase(fileName(relativePath));
    }

    private String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private void copyCapturedFile(Path stagingRoot, Path aflOutputDirectory, CapturedFuzzingFile capturedFile)
            throws IOException {
        Path sourcePath =
                aflOutputDirectory.resolve(capturedFile.relativePath()).normalize();
        if (!sourcePath.startsWith(aflOutputDirectory)) {
            throw ExecutorJobException.validation("AFL++ artifact выходит за пределы output directory");
        }
        Path targetPath = stagingRoot
                .resolve(ARCHIVE_AFL_OUTPUT_DIR)
                .resolve(capturedFile.relativePath())
                .normalize();
        if (!targetPath.startsWith(stagingRoot)) {
            throw ExecutorJobException.validation("AFL++ artifact выходит за пределы fuzzing report bundle");
        }
        Path targetParent = targetPath.getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private FuzzingReport report(
            JobMessage job, FuzzingKernelExecutionResult executionResult, List<CapturedFuzzingFile> capturedFiles) {
        return new FuzzingReport(
                1,
                ARTIFACT_TYPE,
                job.jobExecutionId().toString(),
                executionResult.parameters().mode().wireValue(),
                executionResult.parameters().localGrammar(),
                count(capturedFiles, "crash"),
                count(capturedFiles, "hang"),
                count(capturedFiles, "corpus"),
                fuzzerStats(executionResult.aflOutputDirectory(), capturedFiles),
                capturedFiles.stream().map(this::reportFile).toList());
    }

    private int count(List<CapturedFuzzingFile> capturedFiles, String category) {
        return Math.toIntExact(capturedFiles.stream()
                .filter(file -> category.equals(file.category()))
                .count());
    }

    private Map<String, Map<String, String>> fuzzerStats(
            Path aflOutputDirectory, List<CapturedFuzzingFile> capturedFiles) {
        Map<String, Map<String, String>> stats = new LinkedHashMap<>();
        for (CapturedFuzzingFile capturedFile : capturedFiles) {
            if (!"stats".equals(capturedFile.category())) {
                continue;
            }
            Path statsPath =
                    aflOutputDirectory.resolve(capturedFile.relativePath()).normalize();
            stats.put(capturedFile.relativePath().toString(), parseFuzzerStats(statsPath));
        }
        return Map.copyOf(stats);
    }

    private Map<String, String> parseFuzzerStats(Path statsPath) {
        Map<String, String> stats = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(statsPath, StandardCharsets.UTF_8)) {
                if (!line.contains(":")) {
                    continue;
                }
                String key = StringUtils.trimToEmpty(StringUtils.substringBefore(line, ":"));
                String value = StringUtils.trimToEmpty(StringUtils.substringAfter(line, ":"));
                if (StringUtils.isNotBlank(key)) {
                    stats.put(key, value);
                }
            }
            return Map.copyOf(stats);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.artifacts.stats-read",
                    "Не удалось прочитать AFL++ fuzzer_stats",
                    exception.getMessage(),
                    Map.of(
                            "path",
                            statsPath.toString(),
                            "exceptionClass",
                            exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private FuzzingReportFile reportFile(CapturedFuzzingFile capturedFile) {
        return new FuzzingReportFile(
                capturedFile.category(),
                ARCHIVE_AFL_OUTPUT_DIR + "/" + capturedFile.relativePath(),
                capturedFile.sizeBytes());
    }

    private void createArchive(Path stagingRoot, Path archivePath, long timeoutSeconds) {
        ProcessExecutionResult result = runTar(stagingRoot, archivePath, timeoutSeconds);
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "fuzzing.artifacts.archive.timeout",
                    "Архивация fuzzing artifacts превысила timeout job",
                    null,
                    Map.of("timeoutSeconds", timeoutSeconds),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.artifacts.archive.failed",
                    "tar завершился с ошибкой при архивации fuzzing artifacts",
                    errorDetails(result),
                    Map.of("exitCode", result.exitCode()),
                    ExecutionStatus.FAILED);
        }
        if (!Files.isRegularFile(archivePath)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.artifacts.archive.missing",
                    "tar завершился успешно, но fuzzing report bundle не найден",
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
                    "fuzzing.artifacts.archive.process-runner",
                    "Не удалось запустить tar через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private ArtifactDescriptor upload(JobMessage job, Path archivePath, FuzzingReport report) {
        try {
            return storageClient
                    .upload(new StorageUploadRequest(
                            archivePath,
                            "fuzzing-reports/%s/%s".formatted(job.jobExecutionId(), ARCHIVE_FILE_NAME),
                            ARTIFACT_TYPE,
                            ARCHIVE_FILE_NAME,
                            CONTENT_TYPE,
                            Map.of(
                                    "jobExecutionId",
                                    job.jobExecutionId().toString(),
                                    "archiveFormat",
                                    ARCHIVE_FORMAT,
                                    "reportPath",
                                    REPORT_FILE_NAME,
                                    "crashCount",
                                    report.crashCount(),
                                    "hangCount",
                                    report.hangCount(),
                                    "corpusCount",
                                    report.corpusCount())))
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
                "fuzzing.artifacts.upload-failed",
                "Не удалось загрузить fuzzing report bundle во внутреннее хранилище",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private Map<String, Object> bundleMetadata(ArtifactDescriptor descriptor, FuzzingReport report) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("uri", descriptor.uri());
        metadata.put("fileName", ARCHIVE_FILE_NAME);
        metadata.put("format", ARCHIVE_FORMAT);
        metadata.put("contentType", CONTENT_TYPE);
        metadata.put("reportPath", REPORT_FILE_NAME);
        metadata.put("crashCount", report.crashCount());
        metadata.put("hangCount", report.hangCount());
        metadata.put("corpusCount", report.corpusCount());
        metadata.put("sizeBytes", descriptor.sizeBytes());
        metadata.put("checksumSha256", descriptor.checksumSha256());
        return Map.copyOf(metadata);
    }

    private String logs(ArtifactDescriptor descriptor, FuzzingReport report) {
        return "Fuzzing report tar.gz опубликован: %s (crashes=%d, hangs=%d, corpus=%d)"
                .formatted(descriptor.uri(), report.crashCount(), report.hangCount(), report.corpusCount());
    }

    private void ensureInsideWorkspace(Path workspaceRoot, Path path, String code) {
        if (!path.startsWith(workspaceRoot)) {
            throw new ExecutorJobException(
                    ErrorType.SECURITY_ERROR,
                    code,
                    "Fuzzing artifacts выходят за пределы workspace",
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

    private record CapturedFuzzingFile(String category, Path relativePath, long sizeBytes) {}
}
