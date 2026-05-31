package ru.diplom.cicd.script.runner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
public final class ScriptRunner {

    private static final int ERROR_DETAILS_LIMIT = 1000;
    public static final int MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024;

    private final ProcessRunner processRunner;

    public ScriptRunner(ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public ScriptExecutionResult run(ScriptParameters parameters, ScriptWorkspace workspace, long timeoutSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(workspace, "workspace");

        ProcessExecutionResult result = runProcess(parameters, workspace, Duration.ofSeconds(timeoutSeconds));
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "script.process.timeout",
                    "Bash script превысил timeout job",
                    errorDetails(result),
                    Map.of("timeoutSeconds", timeoutSeconds),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.USER_CODE_ERROR,
                    "script.process.failed",
                    "Bash script завершился с ошибкой",
                    errorDetails(result),
                    Map.of("exitCode", result.exitCode()),
                    ExecutionStatus.FAILED);
        }
        return new ScriptExecutionResult(parameters, result, logs(result));
    }

    private ProcessExecutionResult runProcess(
            ScriptParameters parameters, ScriptWorkspace workspace, Duration timeout) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(
                            List.of("bash", workspace.scriptPath().toString()))
                    .workingDirectory(workspace.workingDirectory())
                    .environment(parameters.environment())
                    .timeout(timeout)
                    .maxOutputBytesPerStream(MAX_OUTPUT_BYTES_PER_STREAM)
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "script.process-runner",
                    "Не удалось запустить bash script через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        } catch (IllegalArgumentException exception) {
            throw new ExecutorJobException(
                    ErrorType.VALIDATION_ERROR,
                    "script.working-directory",
                    "Рабочая директория script job недоступна",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private String logs(ProcessExecutionResult result) {
        StringBuilder logs = new StringBuilder();
        logs.append("Bash script завершен успешно").append(System.lineSeparator());
        appendProcessOutput(logs, result);
        return logs.toString().stripTrailing();
    }

    private void appendProcessOutput(StringBuilder logs, ProcessExecutionResult result) {
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(stdout)) {
            logs.append(stdout.stripTrailing()).append(System.lineSeparator());
        }
        if (result.stdoutTruncated()) {
            appendTruncationMarker(logs, "stdout");
        }
        if (StringUtils.isNotBlank(stderr)) {
            logs.append(stderr.stripTrailing()).append(System.lineSeparator());
        }
        if (result.stderrTruncated()) {
            appendTruncationMarker(logs, "stderr");
        }
    }

    private void appendTruncationMarker(StringBuilder logs, String streamName) {
        logs.append("[")
                .append(streamName)
                .append(" усечен: сохранено не более ")
                .append(MAX_OUTPUT_BYTES_PER_STREAM)
                .append(" байт]")
                .append(System.lineSeparator());
    }

    private String errorDetails(ProcessExecutionResult result) {
        String stderr = StringUtils.trimToEmpty(result.stderrText(StandardCharsets.UTF_8));
        String stdout = StringUtils.trimToEmpty(result.stdoutText(StandardCharsets.UTF_8));
        String details = StringUtils.isNotBlank(stderr) ? stderr : stdout;
        if (StringUtils.isBlank(details) && (result.stdoutTruncated() || result.stderrTruncated())) {
            details = "stdout/stderr bash script был усечен";
        }
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }
}
