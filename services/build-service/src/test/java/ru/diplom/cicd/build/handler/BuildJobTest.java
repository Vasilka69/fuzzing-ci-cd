package ru.diplom.cicd.build.handler;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.build.runner.BuildRunner;
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

class BuildJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000307");

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleMavenJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingProcessRunner processRunner = new CapturingProcessRunner(processResult(0, "maven ok\n", ""));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "build-test-worker-1");
        BuildJob job = new BuildJob(new BuildRunner(processRunner));

        ExecutorEventMessage finishedEvent = handler.handle(buildJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Сборка maven завершена успешно", finishedEvent.summary());
        assertEquals("maven", finishedEvent.additionalData().get("buildTool"));
        assertEquals(".", finishedEvent.additionalData().get("workingDirectory"));
        assertEquals("./mvnw", finishedEvent.additionalData().get("entrypoint"));
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Сборка maven завершена успешно"));
        assertTrue(logEvent.logs().contains("maven ok"));

        assertEquals(List.of("./mvnw", "-q", "test"), processRunner.request.command());
        assertTrue(processRunner.request.workingDirectory().startsWith(tempDir.resolve("workspaces")));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("build", json.get("jobType").textValue());
        assertEquals("build/maven", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals("maven", json.get("additionalData").get("buildTool").textValue());
        assertEquals("./mvnw", json.get("additionalData").get("entrypoint").textValue());
        assertEquals(0, json.get("artifacts").size());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    private JobMessage buildJob() {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                UUID.fromString("00000000-0000-0000-0000-000000000303"),
                UUID.fromString("00000000-0000-0000-0000-000000000304"),
                UUID.fromString("00000000-0000-0000-0000-000000000305"),
                UUID.fromString("00000000-0000-0000-0000-000000000306"),
                JOB_EXECUTION_ID,
                JobType.BUILD,
                "build/maven",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of("build_tool", "maven", "entrypoint", "./mvnw", "args", List.of("-q", "test")),
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

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private static ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(
                        processChunk(ProcessStreamType.STDOUT, 0, stdout),
                        processChunk(ProcessStreamType.STDERR, 1, stderr)));
    }

    private static ProcessOutputChunk processChunk(ProcessStreamType stream, long sequence, String text) {
        return new ProcessOutputChunk(stream, sequence, text.getBytes(StandardCharsets.UTF_8));
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
        public CompletionStage<Void> publish(ExecutorEventMessage logEvent) {
            events.add(logEvent);
            return CompletableFuture.completedFuture(null);
        }
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
}
