package ru.diplom.cicd.vcs.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

@Component
public final class GitCheckoutRunner {

    private static final String GIT_EXECUTABLE = "git";
    private static final int ERROR_DETAILS_LIMIT = 1000;

    public static final String CHECKOUT_PATH_ARGUMENT = "checkoutPath";
    public static final String PARAMETERS_ARGUMENT = "parameters";

    private final ProcessRunner processRunner;

    public GitCheckoutRunner(ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public GitCheckoutResult checkout(GitCheckoutParameters parameters, Path checkoutPath, long timeoutSeconds) {
        Objects.requireNonNull(parameters, PARAMETERS_ARGUMENT);
        Objects.requireNonNull(checkoutPath, CHECKOUT_PATH_ARGUMENT);

        Path normalizedCheckoutPath = checkoutPath.toAbsolutePath().normalize();
        Path parent = normalizedCheckoutPath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.git.workspace",
                    "Не удалось подготовить каталог для Git checkout",
                    exception.getMessage(),
                    Map.of(CHECKOUT_PATH_ARGUMENT, normalizedCheckoutPath.toString()),
                    ExecutionStatus.FAILED);
        }

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        ProcessExecutionResult cloneResult = runGit(
                cloneCommand(parameters, normalizedCheckoutPath),
                parent,
                timeout,
                "vcs.git.checkout-failed",
                "Git clone завершился с ошибкой",
                parameters);
        ProcessExecutionResult revParseResult = runGit(
                List.of(GIT_EXECUTABLE, "rev-parse", "HEAD"),
                normalizedCheckoutPath,
                timeout,
                "vcs.git.rev-parse-failed",
                "Git checkout выполнен, но commit hash не удалось определить",
                parameters);

        String commitHash = StringUtils.trimToEmpty(revParseResult.stdoutText(StandardCharsets.UTF_8));
        if (StringUtils.isBlank(commitHash)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.git.empty-commit-hash",
                    "Git вернул пустой commit hash",
                    null,
                    Map.of(CHECKOUT_PATH_ARGUMENT, normalizedCheckoutPath.toString()),
                    ExecutionStatus.FAILED);
        }

        return new GitCheckoutResult(commitHash, logs(parameters, cloneResult, revParseResult));
    }

    private List<String> cloneCommand(GitCheckoutParameters parameters, Path checkoutPath) {
        List<String> command = new ArrayList<>();
        command.add(GIT_EXECUTABLE);
        command.add("clone");
        command.add("--depth");
        command.add(Integer.toString(parameters.checkoutDepth()));
        command.add("--single-branch");
        if (!"tag".equals(parameters.refType())) {
            command.add("--no-tags");
        }
        if (parameters.ref() != null) {
            command.add("--branch");
            command.add(parameters.ref());
        }
        command.add(parameters.repositoryUrl());
        command.add(checkoutPath.toString());
        return List.copyOf(command);
    }

    private ProcessExecutionResult runGit(
            List<String> command,
            Path workingDirectory,
            Duration timeout,
            String errorCode,
            String errorSummary,
            GitCheckoutParameters parameters) {
        try {
            ProcessExecutionResult result = processRunner.run(ProcessExecutionRequest.builder(command)
                    .workingDirectory(workingDirectory)
                    .timeout(timeout)
                    .build());
            if (result.timedOut()) {
                throw new ExecutorJobException(
                        ErrorType.TIMEOUT,
                        "vcs.git.timeout",
                        "Git checkout превысил timeout job",
                        null,
                        Map.of("timeoutSeconds", timeout.toSeconds()),
                        ExecutionStatus.TIMEOUT);
            }
            if (result.exitCode() != 0) {
                throw new ExecutorJobException(
                        ErrorType.USER_CODE_ERROR,
                        errorCode,
                        errorSummary,
                        errorDetails(parameters, result),
                        Map.of("exitCode", result.exitCode()),
                        ExecutionStatus.FAILED);
            }
            return result;
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "vcs.git.process-runner",
                    "Не удалось запустить Git через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private String logs(
            GitCheckoutParameters parameters,
            ProcessExecutionResult cloneResult,
            ProcessExecutionResult revParseResult) {
        StringBuilder logs = new StringBuilder();
        logs.append("Git checkout shallow clone завершен").append(System.lineSeparator());
        appendProcessOutput(logs, parameters, cloneResult);
        appendProcessOutput(logs, parameters, revParseResult);
        return logs.toString().stripTrailing();
    }

    private void appendProcessOutput(
            StringBuilder logs, GitCheckoutParameters parameters, ProcessExecutionResult result) {
        String stdout =
                StringUtils.trimToEmpty(redactRepositoryUrl(parameters, result.stdoutText(StandardCharsets.UTF_8)));
        String stderr =
                StringUtils.trimToEmpty(redactRepositoryUrl(parameters, result.stderrText(StandardCharsets.UTF_8)));
        if (StringUtils.isNotBlank(stdout)) {
            logs.append(stdout.stripTrailing()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(stderr)) {
            logs.append(stderr.stripTrailing()).append(System.lineSeparator());
        }
    }

    private String errorDetails(GitCheckoutParameters parameters, ProcessExecutionResult result) {
        String stderr =
                StringUtils.trimToEmpty(redactRepositoryUrl(parameters, result.stderrText(StandardCharsets.UTF_8)));
        String stdout =
                StringUtils.trimToEmpty(redactRepositoryUrl(parameters, result.stdoutText(StandardCharsets.UTF_8)));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }

    private String redactRepositoryUrl(GitCheckoutParameters parameters, String text) {
        if (StringUtils.isBlank(text)) {
            return StringUtils.EMPTY;
        }
        return text.replace(parameters.repositoryUrl(), parameters.safeRepositoryUrl());
    }
}
