package ru.diplom.cicd.fuzzing.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
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
        CapturingProcessRunner processRunner = new CapturingProcessRunner(processResult(0, "ipc smoke ok\n", ""));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "fuzzing-test-worker-1");
        FuzzingJob job = new FuzzingJob(new ProcessFuzzingKernelAdapter(processRunner, tempDir.resolve("kernel")));

        ExecutorEventMessage finishedEvent = handler.handle(fuzzingJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Fuzzing-ядро завершило запуск адаптера успешно", finishedEvent.summary());
        assertEquals("fake", finishedEvent.additionalData().get("mode"));
        assertEquals("dsl", finishedEvent.additionalData().get("localGrammar"));
        assertEquals(10L, finishedEvent.additionalData().get("budgetSeconds"));
        assertEquals(
                List.of("make", "ipc-smoke"), finishedEvent.additionalData().get("kernelCommand"));
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertEquals(16, finishedEvent.additionalData().get("llmWorkerQueueSize"));
        assertEquals(1, finishedEvent.additionalData().get("llmWorkerCount"));
        assertEquals(4096, finishedEvent.additionalData().get("maxCandidateChars"));
        assertEquals(
                ProcessFuzzingKernelAdapter.MAX_OUTPUT_BYTES_PER_STREAM,
                finishedEvent.additionalData().get("outputLimitBytesPerStream"));
        assertEquals(false, finishedEvent.additionalData().get("stdoutTruncated"));
        assertEquals(false, finishedEvent.additionalData().get("stderrTruncated"));
        assertEquals(TARGET_ARTIFACT_URI, finishedEvent.additionalData().get("targetArtifactUri"));
        assertEquals(
                List.of("./build/target_dsl"), finishedEvent.additionalData().get("targetCommand"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        assertEquals(List.of("make", "ipc-smoke"), processRunner.request.command());
        assertEquals(tempDir.resolve("kernel").toAbsolutePath().normalize(), processRunner.request.workingDirectory());
        assertEquals("fake", processRunner.request.environment().get("CICD_FUZZING_MODE"));
        assertEquals("dsl", processRunner.request.environment().get("CICD_FUZZING_LOCAL_GRAMMAR"));
        assertEquals("10", processRunner.request.environment().get("CICD_FUZZING_BUDGET_SECONDS"));
        assertEquals(
                JOB_EXECUTION_ID.toString(), processRunner.request.environment().get("CICD_JOB_EXECUTION_ID"));
        assertEquals(TARGET_ARTIFACT_URI, processRunner.request.environment().get("CICD_TARGET_ARTIFACT_URI"));
        assertEquals("", processRunner.request.environment().get("LLM_API_URL"));
        assertEquals("", processRunner.request.environment().get("LLM_API_KEY"));
        assertTrue(processRunner.request.environment().get("LLM_MUTATOR_ADDR").endsWith("llm-mutator.sock"));
        assertTrue(processRunner
                .request
                .environment()
                .get("LLM_MUTATOR_PROMPT_FILE")
                .endsWith("targets/dsl/prompt.txt"));
        assertTrue(
                processRunner.request.environment().get("LLM_MUTATOR_SEED_DIR").endsWith("targets/dsl/seeds"));
        assertEquals("16", processRunner.request.environment().get("LLM_MUTATOR_QUEUE_SIZE"));
        assertEquals("1", processRunner.request.environment().get("LLM_MUTATOR_WORKERS"));
        assertEquals("4096", processRunner.request.environment().get("LLM_MUTATOR_MAX_CANDIDATE_CHARS"));
        assertTrue(processRunner.request.workingDirectory().startsWith(tempDir.resolve("kernel")));

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Fuzzing adapter запустил готовое ядро"));
        assertTrue(logEvent.logs().contains("ipc smoke ok"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("fuzzing", json.get("jobType").textValue());
        assertEquals("fuzzing/afl-llm", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals("fake", json.get("additionalData").get("mode").textValue());
        assertEquals("dsl", json.get("additionalData").get("localGrammar").textValue());
        assertEquals(10L, json.get("additionalData").get("budgetSeconds").longValue());
        assertEquals(16, json.get("additionalData").get("llmWorkerQueueSize").intValue());
        assertEquals(1, json.get("additionalData").get("llmWorkerCount").intValue());
        assertEquals(4096, json.get("additionalData").get("maxCandidateChars").intValue());
        assertEquals(
                "make", json.get("additionalData").get("kernelCommand").get(0).textValue());
        assertEquals(
                "ipc-smoke",
                json.get("additionalData").get("kernelCommand").get(1).textValue());
        assertEquals(
                TARGET_ARTIFACT_URI,
                json.get("additionalData").get("targetArtifactUri").textValue());
        assertEquals(
                "./build/target_dsl",
                json.get("additionalData").get("targetCommand").get(0).textValue());
        assertEquals(0, json.get("artifacts").size());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleFuzzingJobMapsKernelTimeoutToTimeoutStatus() {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingProcessRunner processRunner = new CapturingProcessRunner(processResult(-1, "", "timeout\n", true));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "fuzzing-test-worker-1");
        FuzzingJob job = new FuzzingJob(new ProcessFuzzingKernelAdapter(processRunner, tempDir.resolve("kernel")));

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
                        TARGET_ARTIFACT_URI,
                        "target_command",
                        List.of("./build/target_dsl")),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-30T09:00:00Z"));
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

        private final ProcessExecutionResult result;
        private ProcessExecutionRequest request;

        private CapturingProcessRunner(ProcessExecutionResult result) {
            this.result = result;
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            this.request = request;
            return result;
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
