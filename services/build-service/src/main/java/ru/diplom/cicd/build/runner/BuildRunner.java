package ru.diplom.cicd.build.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;

@Component
public final class BuildRunner {

    private static final int ERROR_DETAILS_LIMIT = 1000;

    private final ProcessRunner processRunner;
    private final BuildCommandBuilder commandBuilder;

    @Autowired
    public BuildRunner(ProcessRunner processRunner) {
        this(processRunner, new BuildCommandBuilder());
    }

    BuildRunner(ProcessRunner processRunner, BuildCommandBuilder commandBuilder) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
    }

    public BuildExecutionResult build(BuildParameters parameters, Path workspaceRoot, long timeoutSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");

        Path workingDirectory = resolveWorkingDirectory(workspaceRoot, parameters.workingDirectory());
        ProcessExecutionResult result = runBuild(parameters, workingDirectory, Duration.ofSeconds(timeoutSeconds));
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "build.process.timeout",
                    "Сборка превысила timeout job",
                    errorDetails(result),
                    Map.of("timeoutSeconds", timeoutSeconds),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.USER_CODE_ERROR,
                    "build.process.failed",
                    "Команда сборки завершилась с ошибкой",
                    errorDetails(result),
                    Map.of(
                            "exitCode",
                            result.exitCode(),
                            "buildTool",
                            parameters.buildTool().wireValue()),
                    ExecutionStatus.FAILED);
        }
        return new BuildExecutionResult(parameters, result, logs(parameters, result));
    }

    private ProcessExecutionResult runBuild(BuildParameters parameters, Path workingDirectory, Duration timeout) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(commandBuilder.command(parameters))
                    .workingDirectory(workingDirectory)
                    .environment(parameters.environment())
                    .timeout(timeout)
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "build.process-runner",
                    "Не удалось запустить build tool через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        } catch (IllegalArgumentException exception) {
            throw new ExecutorJobException(
                    ErrorType.VALIDATION_ERROR,
                    "build.working-directory",
                    "Рабочая директория сборки недоступна",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private Path resolveWorkingDirectory(Path workspaceRoot, Path relativeWorkingDirectory) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path workingDirectory = root.resolve(relativeWorkingDirectory).normalize();
        if (!workingDirectory.startsWith(root)) {
            throw ExecutorJobException.validation("working_directory выходит за пределы workspace");
        }
        if (!Files.isDirectory(workingDirectory)) {
            throw ExecutorJobException.validation("working_directory не существует внутри workspace");
        }
        return workingDirectory;
    }

    private String logs(BuildParameters parameters, ProcessExecutionResult result) {
        StringBuilder logs = new StringBuilder();
        logs.append("Сборка ")
                .append(parameters.buildTool().wireValue())
                .append(" завершена успешно")
                .append(System.lineSeparator());
        appendProcessOutput(logs, result);
        return logs.toString().stripTrailing();
    }

    private void appendProcessOutput(StringBuilder logs, ProcessExecutionResult result) {
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(stdout)) {
            logs.append(stdout.stripTrailing()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(stderr)) {
            logs.append(stderr.stripTrailing()).append(System.lineSeparator());
        }
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }
}
