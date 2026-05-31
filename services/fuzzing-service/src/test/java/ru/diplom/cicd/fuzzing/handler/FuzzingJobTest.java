package ru.diplom.cicd.fuzzing.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.fuzzing.artifact.FuzzingArtifactBundlePublisher;
import ru.diplom.cicd.fuzzing.runner.ProcessFuzzingKernelAdapter;

class FuzzingJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000407");
    private static final String TARGET_ARTIFACT_URI =
            "storage://build-artifacts/00000000-0000-0000-0000-000000000407/target.tar.gz";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleFuzzingJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingProcessRunner processRunner = new CapturingProcessRunner(
                processResult(0, "kernel prepared\n", ""), processResult(0, "afl run ok\n", ""));
        CapturingStorageClient storageClient = new CapturingStorageClient();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "fuzzing-test-worker-1");
        FuzzingJob job = fuzzingJob(processRunner, storageClient);

        ExecutorEventMessage finishedEvent = handler.handle(fuzzingJob(), job);

        assertEquals(3, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        ExecutorEventMessage artifactEvent = eventPublisher.events.get(1);
        assertEquals(EventType.JOB_ARTIFACT, artifactEvent.eventType());
        assertEquals(1, artifactEvent.artifacts().size());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Fuzzing завершен, найдено crash cases: 1", finishedEvent.summary());
        assertEquals(artifactEvent.artifacts(), finishedEvent.artifacts());
        assertEquals("fake", finishedEvent.additionalData().get("mode"));
        assertEquals("dsl", finishedEvent.additionalData().get("localGrammar"));
        assertEquals("dsl", finishedEvent.additionalData().get("demoTarget"));
        assertEquals("targets/dsl/prompt.txt", finishedEvent.additionalData().get("demoTargetPromptPath"));
        assertEquals("targets/dsl/seeds", finishedEvent.additionalData().get("demoTargetSeedCorpusPath"));
        assertEquals("targets/dsl/dsl.dict", finishedEvent.additionalData().get("demoTargetDictionaryPath"));
        assertEquals(10L, finishedEvent.additionalData().get("budgetSeconds"));
        assertEquals(List.of("make", "all"), finishedEvent.additionalData().get("kernelPrepareCommand"));
        @SuppressWarnings("unchecked")
        List<String> kernelCommand =
                (List<String>) finishedEvent.additionalData().get("kernelCommand");
        assertTrue(kernelCommand.getFirst().endsWith("AFLplusplus/afl-fuzz"));
        assertTrue(kernelCommand.contains("-V"));
        assertEquals("10", kernelCommand.get(kernelCommand.indexOf("-V") + 1));
        assertTrue(kernelCommand.contains("--"));
        assertEquals("./build/target_dsl", kernelCommand.getLast());
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertEquals(16, finishedEvent.additionalData().get("llmWorkerQueueSize"));
        assertEquals(1, finishedEvent.additionalData().get("llmWorkerCount"));
        assertEquals(4096, finishedEvent.additionalData().get("maxCandidateChars"));
        assertEquals(
                ProcessFuzzingKernelAdapter.MAX_OUTPUT_BYTES_PER_STREAM,
                finishedEvent.additionalData().get("outputLimitBytesPerStream"));
        assertEquals(false, finishedEvent.additionalData().get("stdoutTruncated"));
        assertEquals(false, finishedEvent.additionalData().get("stderrTruncated"));
        assertEquals(1, finishedEvent.additionalData().get("crashCount"));
        assertEquals(1, finishedEvent.additionalData().get("hangCount"));
        assertEquals(1, finishedEvent.additionalData().get("corpusCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fuzzingReportBundle =
                (Map<String, Object>) finishedEvent.additionalData().get("fuzzingReportBundle");
        assertEquals(
                "storage://fuzzing-reports/" + JOB_EXECUTION_ID + "/fuzzing-report.tar.gz",
                fuzzingReportBundle.get("uri"));
        assertEquals("fuzzing-report.json", fuzzingReportBundle.get("reportPath"));
        assertEquals(1, fuzzingReportBundle.get("crashCount"));
        assertEquals(1, fuzzingReportBundle.get("hangCount"));
        assertEquals(1, fuzzingReportBundle.get("corpusCount"));
        assertEquals(TARGET_ARTIFACT_URI, finishedEvent.additionalData().get("targetArtifactUri"));
        assertEquals(
                List.of("./build/target_dsl"), finishedEvent.additionalData().get("targetCommand"));
        assertEquals(1, finishedEvent.artifacts().size());
        ArtifactDescriptor artifact = finishedEvent.artifacts().getFirst();
        assertEquals("fuzzing_report", artifact.artifactType());
        assertEquals("fuzzing-report.tar.gz", artifact.name());
        assertEquals("application/gzip", artifact.contentType());
        assertEquals("storage://fuzzing-reports/" + JOB_EXECUTION_ID + "/fuzzing-report.tar.gz", artifact.uri());
        assertEquals(1, storageClient.requests.size());
        assertNull(finishedEvent.logs());

        assertEquals(3, processRunner.requests.size());
        assertEquals(List.of("make", "all"), processRunner.requests.getFirst().command());
        ProcessExecutionRequest aflRequest = processRunner.requests.get(1);
        assertTrue(aflRequest.command().getFirst().endsWith("AFLplusplus/afl-fuzz"));
        assertTrue(aflRequest.command().contains("-i"));
        assertTrue(aflRequest.command().contains("-o"));
        assertTrue(aflRequest.command().contains("-x"));
        assertTrue(aflRequest.command().contains("-V"));
        assertEquals("10", aflRequest.command().get(aflRequest.command().indexOf("-V") + 1));
        assertEquals(tempDir.resolve("kernel").toAbsolutePath().normalize(), aflRequest.workingDirectory());
        assertEquals("fake", aflRequest.environment().get("CICD_FUZZING_MODE"));
        assertEquals("dsl", aflRequest.environment().get("CICD_FUZZING_LOCAL_GRAMMAR"));
        assertEquals("10", aflRequest.environment().get("CICD_FUZZING_BUDGET_SECONDS"));
        assertEquals("1", aflRequest.environment().get("AFL_CUSTOM_MUTATOR_ONLY"));
        assertEquals("1", aflRequest.environment().get("AFL_NO_UI"));
        assertEquals("1", aflRequest.environment().get("AFL_SKIP_CPUFREQ"));
        assertTrue(aflRequest.environment().get("AFL_OUTPUT_DIR").contains("afl-output"));
        assertTrue(aflRequest.environment().get("AFL_SEEDS_DIR").endsWith("targets/dsl/seeds"));
        assertTrue(aflRequest.environment().get("AFL_CUSTOM_MUTATOR_LIBRARY").endsWith("build/afl_llm_mutator.so"));
        assertEquals(JOB_EXECUTION_ID.toString(), aflRequest.environment().get("CICD_JOB_EXECUTION_ID"));
        assertEquals(TARGET_ARTIFACT_URI, aflRequest.environment().get("CICD_TARGET_ARTIFACT_URI"));
        assertEquals("", aflRequest.environment().get("LLM_API_URL"));
        assertEquals("", aflRequest.environment().get("LLM_API_KEY"));
        assertTrue(aflRequest.environment().get("LLM_MUTATOR_ADDR").endsWith("llm-mutator.sock"));
        assertTrue(aflRequest.environment().get("LLM_MUTATOR_PROMPT_FILE").endsWith("targets/dsl/prompt.txt"));
        assertTrue(aflRequest.environment().get("LLM_MUTATOR_SEED_DIR").endsWith("targets/dsl/seeds"));
        assertTrue(
                aflRequest.environment().get("CICD_FUZZING_DSL_DICTIONARY_FILE").endsWith("targets/dsl/dsl.dict"));
        assertEquals("16", aflRequest.environment().get("LLM_MUTATOR_QUEUE_SIZE"));
        assertEquals("1", aflRequest.environment().get("LLM_MUTATOR_WORKERS"));
        assertEquals("4096", aflRequest.environment().get("LLM_MUTATOR_MAX_CANDIDATE_CHARS"));
        assertTrue(aflRequest.workingDirectory().startsWith(tempDir.resolve("kernel")));
        assertEquals("tar", processRunner.requests.getLast().command().getFirst());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Fuzzing adapter запустил готовое ядро"));
        assertTrue(logEvent.logs().contains("afl run ok"));
        assertTrue(logEvent.logs().contains("Fuzzing report tar.gz опубликован"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("fuzzing", json.get("jobType").textValue());
        assertEquals("fuzzing/afl-llm", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals("fake", json.get("additionalData").get("mode").textValue());
        assertEquals("dsl", json.get("additionalData").get("localGrammar").textValue());
        assertEquals("dsl", json.get("additionalData").get("demoTarget").textValue());
        assertEquals(
                "targets/dsl/prompt.txt",
                json.get("additionalData").get("demoTargetPromptPath").textValue());
        assertEquals(
                "targets/dsl/seeds",
                json.get("additionalData").get("demoTargetSeedCorpusPath").textValue());
        assertEquals(
                "targets/dsl/dsl.dict",
                json.get("additionalData").get("demoTargetDictionaryPath").textValue());
        assertEquals(10L, json.get("additionalData").get("budgetSeconds").longValue());
        assertEquals(16, json.get("additionalData").get("llmWorkerQueueSize").intValue());
        assertEquals(1, json.get("additionalData").get("llmWorkerCount").intValue());
        assertEquals(4096, json.get("additionalData").get("maxCandidateChars").intValue());
        assertEquals(
                "make",
                json.get("additionalData").get("kernelPrepareCommand").get(0).textValue());
        assertEquals(
                "all",
                json.get("additionalData").get("kernelPrepareCommand").get(1).textValue());
        assertTrue(json.get("additionalData")
                .get("kernelCommand")
                .get(0)
                .textValue()
                .endsWith("AFLplusplus/afl-fuzz"));
        assertEquals(
                TARGET_ARTIFACT_URI,
                json.get("additionalData").get("targetArtifactUri").textValue());
        assertEquals(
                "./build/target_dsl",
                json.get("additionalData").get("targetCommand").get(0).textValue());
        assertEquals(1, json.get("additionalData").get("crashCount").intValue());
        assertEquals(1, json.get("additionalData").get("hangCount").intValue());
        assertEquals(1, json.get("additionalData").get("corpusCount").intValue());
        assertEquals(
                "storage://fuzzing-reports/" + JOB_EXECUTION_ID + "/fuzzing-report.tar.gz",
                json.get("additionalData").get("fuzzingReportBundle").get("uri").textValue());
        assertEquals(1, json.get("artifacts").size());
        assertEquals(
                "fuzzing_report",
                json.get("artifacts").get(0).get("artifactType").textValue());
        assertEquals(
                "storage://fuzzing-reports/" + JOB_EXECUTION_ID + "/fuzzing-report.tar.gz",
                json.get("artifacts").get(0).get("uri").textValue());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleFuzzingJobMapsKernelTimeoutToTimeoutStatus() {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingProcessRunner processRunner = new CapturingProcessRunner(
                processResult(0, "kernel prepared\n", ""), processResult(-1, "", "timeout\n", true));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "fuzzing-test-worker-1");
        FuzzingJob job = fuzzingJob(processRunner, new CapturingStorageClient());

        ExecutorEventMessage finishedEvent = handler.handle(fuzzingJob(), job);

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.TIMEOUT, finishedEvent.status());
        assertNotNull(finishedEvent.error());
        assertEquals(ErrorType.TIMEOUT, finishedEvent.error().type());
        assertEquals("fuzzing.kernel.timeout", finishedEvent.error().code());
        assertTrue(finishedEvent.error().details().contains("timeout"));
        assertTrue(logPublisher.events.isEmpty());
    }

    private JobMessage fuzzingJob() {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                UUID.fromString("00000000-0000-0000-0000-000000000402"),
                UUID.fromString("00000000-0000-0000-0000-000000000403"),
                UUID.fromString("00000000-0000-0000-0000-000000000404"),
                UUID.fromString("00000000-0000-0000-0000-000000000405"),
                UUID.fromString("00000000-0000-0000-0000-000000000406"),
                JOB_EXECUTION_ID,
                JobType.FUZZING,
                "fuzzing/afl-llm",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of(
                        "budget_seconds",
                        10,
                        "local_grammar",
                        "dsl",
                        "llm_worker_queue_size",
                        16,
                        "max_candidate_chars",
                        4096,
                        "target_artifact_uri",
                        TARGET_ARTIFACT_URI),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-30T09:00:00Z"));
    }

    private FuzzingJob fuzzingJob(CapturingProcessRunner processRunner, CapturingStorageClient storageClient) {
        return new FuzzingJob(
                new ProcessFuzzingKernelAdapter(processRunner, tempDir.resolve("kernel")),
                new FuzzingArtifactBundlePublisher(storageClient, processRunner, objectMapper()));
    }

    private SandboxPolicy safeSandboxPolicy() {
        return new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "none",
                List.of(),
                List.of(),
                false,
                Map.of());
    }

    private ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return processResult(exitCode, stdout, stderr, false);
    }

    private ProcessExecutionResult processResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        List<ProcessOutputChunk> chunks = new ArrayList<>();
        if (!stdout.isEmpty()) {
            chunks.add(new ProcessOutputChunk(ProcessStreamType.STDOUT, 0, stdout.getBytes(StandardCharsets.UTF_8)));
        }
        if (!stderr.isEmpty()) {
            chunks.add(new ProcessOutputChunk(ProcessStreamType.STDERR, 1, stderr.getBytes(StandardCharsets.UTF_8)));
        }
        return new ProcessExecutionResult(exitCode, timedOut, false, Duration.ofMillis(250), chunks, Set.of());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private static final class CapturingProcessRunner implements ProcessRunner {

        private final List<ProcessExecutionResult> results;
        private final List<ProcessExecutionRequest> requests = new ArrayList<>();

        private CapturingProcessRunner(ProcessExecutionResult... results) {
            this.results = new ArrayList<>(List.of(results));
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            this.request = request;
            requests.add(request);
            ProcessExecutionResult result = nextResult(request);
            if (isSuccessfulAflRequest(request, result)) {
                writeAflOutput(Path.of(request.environment().get("AFL_OUTPUT_DIR")));
            }
            if (isTarRequest(request, result)) {
                writeTarArchive(request.command());
            }
            return result;
        }

        private ProcessExecutionResult nextResult(ProcessExecutionRequest request) {
            if (results.isEmpty() && "tar".equals(request.command().getFirst())) {
                return new ProcessExecutionResult(0, false, false, Duration.ofMillis(50), List.of(), Set.of());
            }
            return results.removeFirst();
        }

        private boolean isSuccessfulAflRequest(ProcessExecutionRequest request, ProcessExecutionResult result) {
            return request.command().getFirst().endsWith("AFLplusplus/afl-fuzz")
                    && !result.timedOut()
                    && result.exitCode() == 0;
        }

        private boolean isTarRequest(ProcessExecutionRequest request, ProcessExecutionResult result) {
            return "tar".equals(request.command().getFirst()) && !result.timedOut() && result.exitCode() == 0;
        }

        private void writeAflOutput(Path aflOutputDirectory) {
            try {
                Path defaultOutput = aflOutputDirectory.resolve("default");
                Files.createDirectories(defaultOutput.resolve("crashes"));
                Files.createDirectories(defaultOutput.resolve("hangs"));
                Files.createDirectories(defaultOutput.resolve("queue"));
                Files.writeString(defaultOutput.resolve("crashes/id:000000,sig:06,src:000000"), "crash");
                Files.writeString(defaultOutput.resolve("hangs/id:000000,src:000000"), "hang");
                Files.writeString(defaultOutput.resolve("queue/id:000000,src:000000"), "seed");
                Files.writeString(defaultOutput.resolve("fuzzer_stats"), "execs_done : 42\nsaved_crashes : 1\n");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void writeTarArchive(List<String> command) {
            try {
                Path archivePath = Path.of(command.get(command.indexOf("-czf") + 1));
                Files.createDirectories(archivePath.getParent());
                Files.writeString(archivePath, "fake fuzzing report archive");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class CapturingStorageClient implements StorageClient {

        private final List<StorageUploadRequest> requests = new ArrayList<>();

        @Override
        public CompletionStage<ArtifactDescriptor> upload(StorageUploadRequest request) {
            requests.add(request);
            try {
                String uri = "storage://" + request.destinationPath();
                return CompletableFuture.completedFuture(new ArtifactDescriptor(
                        UUID.nameUUIDFromBytes(uri.getBytes(StandardCharsets.UTF_8)),
                        request.artifactType(),
                        request.name(),
                        uri,
                        request.contentType(),
                        Files.size(request.sourcePath()),
                        "sha256",
                        request.metadata()));
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<Path> download(StorageDownloadRequest request) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("download не используется в тесте"));
        }
    }

    private static final class CapturingEventPublisher implements ExecutorEventPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class CapturingLogPublisher implements ExecutorLogPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }
    }
}
