package ru.diplom.cicd.deploy.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;
import ru.diplom.cicd.executor.core.storage.StorageUris;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

/**
 * MVP ssh-bash runner: доставляет artifact через системный scp и выполняет команды через ssh.
 * Значения секретов не принимает: credentials_ref остается ссылкой для будущего SecretResolver.
 * TODO: подключить SecretResolver и key material injection после появления доверенного secret adapter.
 */
@SuppressWarnings("java:S1192")
public final class SshBashDeploymentRunner {

    public static final int MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024;

    private final StorageClient storageClient;
    private final ProcessRunner processRunner;
    private final String sshExecutable;
    private final String scpExecutable;

    public SshBashDeploymentRunner(StorageClient storageClient, ProcessRunner processRunner) {
        this(storageClient, processRunner, "ssh", "scp");
    }

    public SshBashDeploymentRunner(
            StorageClient storageClient, ProcessRunner processRunner, String sshExecutable, String scpExecutable) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.sshExecutable = requireExecutable(sshExecutable, "sshExecutable");
        this.scpExecutable = requireExecutable(scpExecutable, "scpExecutable");
    }

    public SshBashDeploymentResult deploy(
            JobMessage job, WorkspaceHandle workspace, SshBashDeploymentParameters parameters) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(parameters, "parameters");

        long deadlineNanos =
                System.nanoTime() + Duration.ofSeconds(job.timeoutSeconds()).toNanos();
        Path downloadedArtifact = workspace.root().resolve("deploy-artifact").resolve(sourceFileName(parameters));
        Path downloadedPath = download(parameters.artifactUri(), downloadedArtifact);
        long artifactBytes = size(downloadedPath);
        String artifactChecksum = checksum(downloadedPath);

        ProcessExecutionResult backupResult = null;
        if (parameters.backupExisting()) {
            backupResult = runChecked(
                    backupCommand(parameters),
                    "deploy.ssh-bash.backup",
                    "SSH Bash deployment не смог подготовить backup existing artifact",
                    ErrorType.INFRASTRUCTURE_ERROR,
                    deadlineNanos);
        }
        ProcessExecutionResult copyResult = runChecked(
                copyCommand(downloadedPath, parameters),
                "deploy.ssh-bash.copy",
                "SSH Bash deployment не смог скопировать artifact через scp",
                ErrorType.INFRASTRUCTURE_ERROR,
                deadlineNanos);

        List<ProcessExecutionResult> commandResults = new ArrayList<>();
        for (String command : parameters.commands()) {
            commandResults.add(runChecked(
                    bashCommand(parameters, command),
                    "deploy.ssh-bash.command",
                    "SSH Bash deployment команда завершилась с ошибкой",
                    ErrorType.USER_CODE_ERROR,
                    deadlineNanos));
        }
        DeploymentHealthcheckResult healthcheck = healthcheck(parameters, deadlineNanos);

        return new SshBashDeploymentResult(
                parameters,
                downloadedPath,
                artifactBytes,
                artifactChecksum,
                copyResult,
                backupResult,
                commandResults,
                healthcheck);
    }

    private String sourceFileName(SshBashDeploymentParameters parameters) {
        Path fileName =
                Path.of(StorageUris.namespacePath(parameters.artifactUri())).getFileName();
        if (fileName == null) {
            throw ExecutorJobException.validation("artifact_uri должен указывать на файл артефакта");
        }
        return fileName.toString();
    }

    private Path download(String artifactUri, Path targetPath) {
        try {
            return storageClient
                    .download(new StorageDownloadRequest(artifactUri, targetPath))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw infrastructureError("SSH Bash deployment не смог скачать artifact из storage", exception);
        }
    }

    private long size(Path path) {
        try {
            return java.nio.file.Files.size(path);
        } catch (IOException exception) {
            throw infrastructureError("SSH Bash deployment не смог определить размер artifact", exception);
        }
    }

    private String checksum(Path path) {
        try {
            return StorageChecksums.sha256(path);
        } catch (IOException exception) {
            throw infrastructureError("SSH Bash deployment не смог посчитать SHA-256 artifact", exception);
        }
    }

    private ProcessExecutionResult runChecked(
            List<String> command, String code, String message, ErrorType failureType, long deadlineNanos) {
        Duration timeout = remainingTimeout(deadlineNanos);
        ProcessExecutionResult result = run(command, timeout);
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    code + ".timeout",
                    "SSH Bash deployment превысил timeout job",
                    errorDetails(result),
                    Map.of("timeoutSecondsRemaining", timeout.toSeconds()),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    failureType,
                    code,
                    message,
                    errorDetails(result),
                    Map.of("exitCode", result.exitCode()),
                    ExecutionStatus.FAILED);
        }
        return result;
    }

    private ProcessExecutionResult run(List<String> command, Duration timeout) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(command)
                    .timeout(timeout)
                    .maxOutputBytesPerStream(MAX_OUTPUT_BYTES_PER_STREAM)
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "deploy.ssh-bash.process-runner",
                    "Не удалось запустить ssh/scp через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        } catch (IllegalArgumentException exception) {
            throw new ExecutorJobException(
                    ErrorType.VALIDATION_ERROR,
                    "deploy.ssh-bash.command",
                    "Некорректная команда ssh/scp",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private Duration remainingTimeout(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos < 1) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "deploy.ssh-bash.timeout",
                    "SSH Bash deployment превысил timeout job",
                    null,
                    Map.of(),
                    ExecutionStatus.TIMEOUT);
        }
        return Duration.ofNanos(remainingNanos);
    }

    private List<String> copyCommand(Path sourcePath, SshBashDeploymentParameters parameters) {
        List<String> command = new ArrayList<>(scpBase(parameters.target()));
        command.add(sourcePath.toString());
        command.add(remoteLogin(parameters.target()) + ":" + parameters.destinationPath());
        return List.copyOf(command);
    }

    private List<String> backupCommand(SshBashDeploymentParameters parameters) {
        List<String> command = new ArrayList<>(sshBase(parameters.target()));
        command.add("sh");
        command.add("-c");
        command.add("if [ -e \"$1\" ]; then cp -a \"$1\" \"$1.bak\"; fi");
        command.add("sh");
        command.add(parameters.destinationPath().toString());
        return List.copyOf(command);
    }

    private List<String> bashCommand(SshBashDeploymentParameters parameters, String bashCommand) {
        List<String> command = new ArrayList<>(sshBase(parameters.target()));
        command.add("bash");
        command.add("-lc");
        command.add(bashCommand);
        return List.copyOf(command);
    }

    private DeploymentHealthcheckResult healthcheck(SshBashDeploymentParameters parameters, long deadlineNanos) {
        if (!parameters.healthcheck().enabled()) {
            return DeploymentHealthcheckResult.skipped("ssh_file_exists");
        }

        long startedAt = System.nanoTime();
        ProcessExecutionResult result = run(healthcheckCommand(parameters), remainingTimeout(deadlineNanos));
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "deploy.healthcheck.timeout",
                    "Deploy healthcheck превысил timeout job",
                    errorDetails(result),
                    Map.of("destinationPath", parameters.destinationPath().toString()),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "deploy.healthcheck.failed",
                    "Deploy healthcheck не подтвердил наличие artifact на SSH target",
                    errorDetails(result),
                    Map.of(
                            "targetHost",
                            parameters.target().host(),
                            "destinationPath",
                            parameters.destinationPath().toString(),
                            "exitCode",
                            result.exitCode()),
                    ExecutionStatus.FAILED);
        }
        return DeploymentHealthcheckResult.success(
                "ssh_file_exists",
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                "SSH target подтвердил наличие artifact");
    }

    private List<String> healthcheckCommand(SshBashDeploymentParameters parameters) {
        List<String> command = new ArrayList<>(sshBase(parameters.target()));
        command.add("sh");
        command.add("-c");
        command.add("test -f \"$1\"");
        command.add("sh");
        command.add(parameters.destinationPath().toString());
        return List.copyOf(command);
    }

    private List<String> sshBase(SshBashTarget target) {
        return List.of(
                sshExecutable, "-p", Integer.toString(target.port()), "-o", "BatchMode=yes", remoteLogin(target));
    }

    private List<String> scpBase(SshBashTarget target) {
        return List.of(scpExecutable, "-P", Integer.toString(target.port()), "-o", "BatchMode=yes");
    }

    private String remoteLogin(SshBashTarget target) {
        return target.user() + "@" + target.host();
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = !stderr.isBlank() ? stderr : stdout;
        if (details.isBlank() && (result.stdoutTruncated() || result.stderrTruncated())) {
            details = "stdout/stderr ssh/scp был усечен";
        }
        return abbreviate(details, 1000);
    }

    private ExecutorJobException infrastructureError(String message, Exception exception) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "deploy.ssh-bash.infrastructure",
                message,
                exception.getMessage(),
                Map.of("exceptionClass", exception.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private String requireExecutable(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " не должен быть пустым");
        }
        return value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
