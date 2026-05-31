package ru.diplom.cicd.fuzzing.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

@SuppressWarnings("java:S1192")
@Component
public final class ProcessFuzzingKernelAdapter implements FuzzingKernelAdapter {

    public static final int MAX_OUTPUT_BYTES_PER_STREAM = 64 * 1024;

    private static final int ERROR_DETAILS_LIMIT = 1000;
    private static final String DEFAULT_FAKE_WORKER_SOCKET = "llm-mutator.sock";
    private static final List<String> DEFAULT_PREPARE_COMMAND = List.of("make", "all");
    private static final String DEFAULT_AFLPP_DIR = "../AFLplusplus";
    private static final String AFL_FUZZ_BINARY = "afl-fuzz";
    private static final String DEFAULT_PYTHON = "python3";
    private static final Duration FAKE_WORKER_STARTUP_WAIT = Duration.ofSeconds(1);
    private static final Duration FAKE_WORKER_STOP_WAIT = Duration.ofSeconds(2);

    private final ProcessRunner processRunner;
    private final Path kernelRoot;
    private final Path aflppRoot;
    private final boolean startFakeWorker;

    @Autowired
    public ProcessFuzzingKernelAdapter(
            ProcessRunner processRunner,
            @Value("${cicd.fuzzing.kernel-root:fuzzing-engine/afl-llm-engine}") Path kernelRoot,
            @Value("${cicd.fuzzing.aflpp-root:../AFLplusplus}") Path aflppRoot,
            @Value("${cicd.fuzzing.start-fake-worker:true}") boolean startFakeWorker) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.kernelRoot = Objects.requireNonNull(kernelRoot, "kernelRoot");
        this.aflppRoot = normalizeAflppRoot(kernelRoot, aflppRoot);
        this.startFakeWorker = startFakeWorker;
    }

    public ProcessFuzzingKernelAdapter(ProcessRunner processRunner, Path kernelRoot) {
        this(processRunner, kernelRoot, Path.of(DEFAULT_AFLPP_DIR), false);
    }

    private Path normalizeAflppRoot(Path kernelRoot, Path configuredAflppRoot) {
        Objects.requireNonNull(configuredAflppRoot, "aflppRoot");
        if (configuredAflppRoot.isAbsolute()) {
            return configuredAflppRoot.normalize();
        }
        return kernelRoot
                .toAbsolutePath()
                .normalize()
                .resolve(configuredAflppRoot)
                .normalize();
    }

    @Override
    public FuzzingKernelExecutionResult run(JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(parameters, "parameters");

        Path workingDirectory = workingDirectory(parameters);
        Map<String, String> environment = environment(job, workspace, parameters);
        List<String> prepareCommand = prepareCommand(parameters);
        if (!prepareCommand.isEmpty()) {
            ProcessExecutionResult prepareResult =
                    runProcess(workingDirectory, environment, prepareCommand, Duration.ofSeconds(job.timeoutSeconds()));
            verifyPrepareResult(parameters, prepareResult);
        }

        List<String> command = command(workspace, parameters);
        ProcessExecutionResult result;
        Process fakeWorker = startFakeWorkerIfNeeded(workingDirectory, environment, workspace, parameters);
        try {
            result = runProcess(workingDirectory, environment, command, Duration.ofSeconds(job.timeoutSeconds()));
        } finally {
            stopFakeWorker(fakeWorker);
        }
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "fuzzing.kernel.timeout",
                    "Fuzzing-ядро превысило timeoutSeconds job",
                    errorDetails(result),
                    Map.of("budgetSeconds", parameters.budgetSeconds(), "jobTimeoutSeconds", job.timeoutSeconds()),
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
        return new FuzzingKernelExecutionResult(parameters, prepareCommand, command, result, logs(parameters, result));
    }

    private Process startFakeWorkerIfNeeded(
            Path workingDirectory,
            Map<String, String> environment,
            WorkspaceHandle workspace,
            FuzzingParameters parameters) {
        // AFL++ остается основным процессом под ProcessRunner timeout; fake worker живет только как дочерний IPC
        // helper.
        if (!startFakeWorker || !parameters.kernelCommand().isEmpty() || parameters.mode() != FuzzingMode.FAKE) {
            return null;
        }
        List<String> command = List.of(
                StringUtils.defaultIfBlank(environment.get("PYTHON"), DEFAULT_PYTHON),
                kernelRoot
                        .toAbsolutePath()
                        .normalize()
                        .resolve("src/worker/llm_mutator_server.py")
                        .toString());
        try {
            Path generatedRoot = workspace.root().resolve("generated").normalize();
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.environment().putAll(environment);
            builder.redirectOutput(
                    generatedRoot.resolve("fake-worker.stdout.log").toFile());
            builder.redirectError(
                    generatedRoot.resolve("fake-worker.stderr.log").toFile());
            Process process = builder.start();
            waitForFakeWorker(process);
            return process;
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.fake-worker.start",
                    "Не удалось запустить fake LLM worker для AFL++",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void waitForFakeWorker(Process process) {
        try {
            Thread.sleep(FAKE_WORKER_STARTUP_WAIT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.fake-worker.interrupted",
                    "Ожидание запуска fake LLM worker было прервано",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
        if (!process.isAlive()) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.fake-worker.exited",
                    "Fake LLM worker завершился до запуска AFL++",
                    "Проверьте workspace log fake-worker.stderr.log",
                    Map.of("exitCode", process.exitValue()),
                    ExecutionStatus.FAILED);
        }
    }

    private void stopFakeWorker(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.toHandle().descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(FAKE_WORKER_STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private void verifyPrepareResult(FuzzingParameters parameters, ProcessExecutionResult result) {
        if (result.timedOut()) {
            throw new ExecutorJobException(
                    ErrorType.TIMEOUT,
                    "fuzzing.kernel.prepare-timeout",
                    "Подготовка fuzzing-ядра превысила timeoutSeconds job",
                    errorDetails(result),
                    Map.of("mode", parameters.mode().wireValue()),
                    ExecutionStatus.TIMEOUT);
        }
        if (result.exitCode() != 0) {
            throw new ExecutorJobException(
                    ErrorType.USER_CODE_ERROR,
                    "fuzzing.kernel.prepare-failed",
                    "Подготовка fuzzing-ядра завершилась с ошибкой",
                    errorDetails(result),
                    Map.of(
                            "exitCode",
                            result.exitCode(),
                            "mode",
                            parameters.mode().wireValue()),
                    ExecutionStatus.FAILED);
        }
    }

    private ProcessExecutionResult runProcess(
            Path workingDirectory, Map<String, String> environment, List<String> command, Duration timeout) {
        try {
            return processRunner.run(ProcessExecutionRequest.builder(command)
                    .workingDirectory(workingDirectory)
                    .environment(environment)
                    .timeout(timeout)
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

    private List<String> prepareCommand(FuzzingParameters parameters) {
        if (!parameters.kernelCommand().isEmpty()) {
            return List.of();
        }
        return DEFAULT_PREPARE_COMMAND;
    }

    private List<String> command(WorkspaceHandle workspace, FuzzingParameters parameters) {
        if (!parameters.kernelCommand().isEmpty()) {
            return parameters.kernelCommand();
        }
        return aflFuzzCommand(workspace, parameters);
    }

    private List<String> aflFuzzCommand(WorkspaceHandle workspace, FuzzingParameters parameters) {
        Path root = kernelRoot.toAbsolutePath().normalize();
        List<String> command = new ArrayList<>();
        command.add(aflFuzzExecutable().toString());
        command.add("-i");
        command.add(aflSeedsDirectory(parameters).toString());
        command.add("-o");
        command.add(aflOutputDirectory(workspace, parameters).toString());
        command.add("-x");
        command.add(root.resolve(FuzzingParameters.DEFAULT_DSL_DICTIONARY_FILE)
                .normalize()
                .toString());
        command.add("-V");
        command.add(Long.toString(parameters.budgetSeconds()));
        command.add("--");
        command.addAll(parameters.targetCommand());
        return List.copyOf(command);
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

    private Path aflFuzzExecutable() {
        return aflppRoot.toAbsolutePath().normalize().resolve(AFL_FUZZ_BINARY);
    }

    private Map<String, String> environment(JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters) {
        Map<String, String> environment = new LinkedHashMap<>(parameters.environment());
        Path root = kernelRoot.toAbsolutePath().normalize();
        environment.put("CICD_JOB_EXECUTION_ID", job.jobExecutionId().toString());
        environment.put("CICD_FUZZING_MODE", parameters.mode().wireValue());
        environment.put("CICD_FUZZING_LOCAL_GRAMMAR", parameters.localGrammar());
        environment.put("CICD_FUZZING_BUDGET_SECONDS", Long.toString(parameters.budgetSeconds()));
        environment.put("CICD_WORKSPACE_ROOT", workspace.root().toString());
        environment.put("AFLPP_DIR", aflppRoot.toAbsolutePath().normalize().toString());
        environment.put(
                "AFL_OUTPUT_DIR", aflOutputDirectory(workspace, parameters).toString());
        environment.put("AFL_SEEDS_DIR", aflSeedsDirectory(parameters).toString());
        environment.put(
                "AFL_CUSTOM_MUTATOR_LIBRARY",
                root.resolve("build/afl_llm_mutator.so").toString());
        environment.putIfAbsent("AFL_CUSTOM_MUTATOR_ONLY", "1");
        environment.putIfAbsent("AFL_NO_UI", "1");
        environment.putIfAbsent("AFL_SKIP_CPUFREQ", "1");
        createWorkspaceDirectories(workspace, parameters);
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

    private void createWorkspaceDirectories(WorkspaceHandle workspace, FuzzingParameters parameters) {
        try {
            Files.createDirectories(aflOutputDirectory(workspace, parameters));
            Files.createDirectories(workspace.root().resolve("generated"));
            Files.createDirectories(workspace.root().resolve("generated/discovered"));
            Files.createDirectories(workspace.root().resolve("generated/candidates"));
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "fuzzing.workspace-directories",
                    "Не удалось подготовить workspace-директории для AFL++",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private Path aflOutputDirectory(WorkspaceHandle workspace, FuzzingParameters parameters) {
        return workspace
                .root()
                .resolve("afl-output")
                .resolve(parameters.mode().wireValue())
                .normalize();
    }

    private Path aflSeedsDirectory(FuzzingParameters parameters) {
        if (FuzzingParameters.DSL_GRAMMAR.equals(parameters.localGrammar())) {
            return kernelRoot
                    .toAbsolutePath()
                    .normalize()
                    .resolve(FuzzingParameters.DEFAULT_DSL_SEED_DIR)
                    .normalize();
        }
        return kernelRoot.toAbsolutePath().normalize().resolve("seeds").normalize();
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
        environment.put(
                "LLM_MUTATOR_PROMPT_FILE",
                root.resolve(FuzzingParameters.DEFAULT_DSL_PROMPT_FILE).toString());
        environment.put(
                "LLM_MUTATOR_SEED_DIR",
                root.resolve(FuzzingParameters.DEFAULT_DSL_SEED_DIR).toString());
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
                root.resolve(FuzzingParameters.DEFAULT_DSL_DICTIONARY_FILE).toString());
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
