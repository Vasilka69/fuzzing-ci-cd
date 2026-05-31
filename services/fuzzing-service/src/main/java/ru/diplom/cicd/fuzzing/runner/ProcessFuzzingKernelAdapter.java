package ru.diplom.cicd.fuzzing.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunnerException;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

@Component
public final class ProcessFuzzingKernelAdapter implements FuzzingKernelAdapter {

    public static final int MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024;

    private static final int ERROR_DETAILS_LIMIT = 1000;
    private static final String DEFAULT_FAKE_WORKER_SOCKET = "llm-mutator.sock";
    private static final String DSL_PROMPT_FILE = "targets/dsl/prompt.txt";
    private static final String DSL_SEED_DIR = "targets/dsl/seeds";
    private static final String DSL_DICTIONARY_FILE = "targets/dsl/dsl.dict";
    // TODO(FUZZING-004): заменить smoke-команду на ограниченный по budget_seconds AFL++ запуск.
    private static final List<String> DEFAULT_KERNEL_COMMAND = List.of("make", "ipc-smoke");

    private final ProcessRunner processRunner;
    private final Path kernelRoot;

    public ProcessFuzzingKernelAdapter(
            ProcessRunner processRunner,
            @Value("${cicd.fuzzing.kernel-root:fuzzing-engine/afl-llm-engine}") Path kernelRoot) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.kernelRoot = Objects.requireNonNull(kernelRoot, "kernelRoot");
    }

    @Override
    public FuzzingKernelExecutionResult run(JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(parameters, "parameters");

        List<String> command = command(parameters);
        ProcessExecutionResult result = runProcess(job, workspace, parameters, command);
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "fuzzing.kernel.timeout",
                    "Fuzzing-ядро превысило budget_seconds",
                    errorDetails(result),
                    Map.of("budgetSeconds", parameters.budgetSeconds()),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.USER_CODE_ERROR,
                    "fuzzing.kernel.failed",
                    "Fuzzing-ядро завершилось с ошибкой",
                    errorDetails(result),
                    Map.of(
                            "exitCode",
                            result.exitCode(),
                            "mode",
                            parameters.mode().wireValue()),
                    ExecutionStatus.FAILED);
        }
        return new FuzzingKernelExecutionResult(parameters, command, result, logs(parameters, result));
    }

    private ProcessExecutionResult runProcess(
            JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters, List<String> command) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(command)
                    .workingDirectory(workingDirectory(parameters))
                    .environment(environment(job, workspace, parameters))
                    .timeout(Duration.ofSeconds(parameters.budgetSeconds()))
                    .maxOutputBytesPerStream(MAX_OUTPUT_BYTES_PER_STREAM)
                    .build());
        } catch (ProcessRunnerException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.process-runner",
                    "Не удалось запустить fuzzing-ядро через process runner",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        } catch (IllegalArgumentException exception) {
            throw new ExecutorJobException(
                    ErrorType.VALIDATION_ERROR,
                    "fuzzing.kernel-request",
                    "Параметры запуска fuzzing-ядра некорректны",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private List<String> command(FuzzingParameters parameters) {
        if (!parameters.kernelCommand().isEmpty()) {
            return parameters.kernelCommand();
        }
        return DEFAULT_KERNEL_COMMAND;
    }

    private Path workingDirectory(FuzzingParameters parameters) {
        Path root = kernelRoot.toAbsolutePath().normalize();
        Path workingDirectory =
                root.resolve(parameters.kernelWorkingDirectory()).normalize();
        if (!workingDirectory.startsWith(root)) {
            throw ExecutorJobException.validation("kernel_working_directory выходит за пределы fuzzing kernel root");
        }
        return workingDirectory;
    }

    private Map<String, String> environment(JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters) {
        Map<String, String> environment = new LinkedHashMap<>(parameters.environment());
        environment.put("CICD_JOB_EXECUTION_ID", job.jobExecutionId().toString());
        environment.put("CICD_FUZZING_MODE", parameters.mode().wireValue());
        environment.put("CICD_FUZZING_LOCAL_GRAMMAR", parameters.localGrammar());
        environment.put("CICD_FUZZING_BUDGET_SECONDS", Long.toString(parameters.budgetSeconds()));
        environment.put("CICD_WORKSPACE_ROOT", workspace.root().toString());
        if (parameters.mode() == FuzzingMode.FAKE) {
            appendFakeWorkerEnvironment(environment, workspace, parameters);
        }
        putIfPresent(environment, "CICD_TARGET_ARTIFACT_URI", parameters.targetArtifactUri());
        putIfPresent(environment, "CICD_SOURCE_SNAPSHOT_URI", parameters.sourceSnapshotUri());
        putIfPresent(environment, "CICD_SEED_CORPUS_URI", parameters.seedCorpusUri());
        putIfPresent(environment, "CICD_DICTIONARY_URI", parameters.dictionaryUri());
        putIfPresent(environment, "CICD_PROMPT_URI", parameters.promptUri());
        return Map.copyOf(environment);
    }

    private void appendFakeWorkerEnvironment(
            Map<String, String> environment, WorkspaceHandle workspace, FuzzingParameters parameters) {
        Path root = kernelRoot.toAbsolutePath().normalize();
        Path generatedRoot = workspace.root().resolve("generated").normalize();
        environment.put("LLM_API_URL", "");
        environment.put("LLM_API_KEY", "");
        environment.put(
                "LLM_MUTATOR_ADDR",
                generatedRoot.resolve(DEFAULT_FAKE_WORKER_SOCKET).toString());
        environment.put("LLM_MUTATOR_PROMPT_FILE", root.resolve(DSL_PROMPT_FILE).toString());
        environment.put("LLM_MUTATOR_SEED_DIR", root.resolve(DSL_SEED_DIR).toString());
        environment.put(
                "LLM_MUTATOR_DISCOVERED_DIR",
                generatedRoot.resolve("discovered").toString());
        environment.put(
                "LLM_MUTATOR_LOG_CANDIDATES_DIR",
                generatedRoot.resolve("candidates").toString());
        environment.put("LLM_MUTATOR_QUEUE_SIZE", Integer.toString(parameters.llmWorkerQueueSize()));
        environment.put("LLM_MUTATOR_WORKERS", Integer.toString(parameters.llmWorkerCount()));
        environment.put("LLM_MUTATOR_MAX_CANDIDATE_CHARS", Integer.toString(parameters.maxCandidateChars()));
        environment.put(
                "CICD_FUZZING_DSL_DICTIONARY_FILE",
                root.resolve(DSL_DICTIONARY_FILE).toString());
    }

    private void putIfPresent(Map<String, String> environment, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            environment.put(key, value);
        }
    }

    private String logs(FuzzingParameters parameters, ProcessExecutionResult result) {
        StringBuilder logs = new StringBuilder();
        logs.append("Fuzzing adapter запустил готовое ядро в режиме ")
                .append(parameters.mode().wireValue())
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
            details = "stdout/stderr fuzzing-ядра был усечен";
        }
        return StringUtils.abbreviate(details, ERROR_DETAILS_LIMIT);
    }
}
