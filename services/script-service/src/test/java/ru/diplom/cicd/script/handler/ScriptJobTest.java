package ru.diplom.cicd.script.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.script.artifact.ScriptExpectedOutputResolver;
import ru.diplom.cicd.script.artifact.ScriptOutputPublisher;
import ru.diplom.cicd.script.runner.ScriptRunner;
import ru.diplom.cicd.script.runner.ScriptWorkspacePreparer;

class ScriptJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000907");
    private static final String INPUT_URI = "storage://script-inputs/00000000-0000-0000-0000-000000000907/input.txt";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleBashJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        LocalStorageClient storageClient = storageClientWithInput();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "script-test-worker-1");
        ScriptJob job = scriptJob(storageClient);

        ExecutorEventMessage finishedEvent = handler.handle(scriptJobMessage(), job);

        assertEquals(3, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        ExecutorEventMessage artifactEvent = eventPublisher.events.get(1);
        assertEquals(EventType.JOB_ARTIFACT, artifactEvent.eventType());
        assertEquals(ExecutionStatus.RUNNING, artifactEvent.status());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Bash script завершен успешно", finishedEvent.summary());
        assertEquals("bash", finishedEvent.additionalData().get("scriptType"));
        assertEquals(".", finishedEvent.additionalData().get("workingDirectory"));
        assertEquals("none", finishedEvent.additionalData().get("effectiveNetworkPolicy"));
        assertEquals(
                "executor_container_process", finishedEvent.additionalData().get("sandboxRunner"));
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertEquals(
                ScriptRunner.MAX_OUTPUT_BYTES_PER_STREAM,
                finishedEvent.additionalData().get("outputLimitBytesPerStream"));
        assertEquals(false, finishedEvent.additionalData().get("stdoutTruncated"));
        assertEquals(false, finishedEvent.additionalData().get("stderrTruncated"));
        assertEquals(List.of("out/*.txt"), finishedEvent.additionalData().get("expectedOutputPatterns"));
        assertEquals(1, finishedEvent.artifacts().size());
        assertEquals(artifactEvent.artifacts(), finishedEvent.artifacts());
        ArtifactDescriptor outputArtifact = finishedEvent.artifacts().getFirst();
        assertEquals("script_output", outputArtifact.artifactType());
        assertEquals("out/result.txt", outputArtifact.name());
        assertEquals("storage://script-outputs/%s/out/result.txt".formatted(JOB_EXECUTION_ID), outputArtifact.uri());
        assertNull(finishedEvent.logs());

        Path uploadedOutput = tempDir.resolve("storage/script-outputs/%s/out/result.txt".formatted(JOB_EXECUTION_ID));
        assertTrue(Files.isRegularFile(uploadedOutput));
        assertEquals("payload from storage\n", Files.readString(uploadedOutput));

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Script input artifacts скачаны: 1 файлов"));
        assertTrue(logEvent.logs().contains("Bash script завершен успешно"));
        assertTrue(logEvent.logs().contains("stdout: hello from env"));
        assertTrue(logEvent.logs().contains("stderr: diagnostic"));
        assertTrue(logEvent.logs().contains("Script expected outputs опубликованы: 1 файлов"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("script", json.get("jobType").textValue());
        assertEquals("script/bash", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals("bash", json.get("additionalData").get("scriptType").textValue());
        assertEquals(
                "none", json.get("additionalData").get("effectiveNetworkPolicy").textValue());
        assertEquals(
                "executor_container_process",
                json.get("additionalData").get("sandboxRunner").textValue());
        assertEquals(
                "inputs/input.txt",
                json.get("additionalData")
                        .get("inputArtifacts")
                        .get(0)
                        .get("path")
                        .textValue());
        assertEquals(
                "out/result.txt",
                json.get("additionalData")
                        .get("expectedOutputs")
                        .get(0)
                        .get("path")
                        .textValue());
        assertEquals(
                outputArtifact.uri(),
                json.get("additionalData")
                        .get("outputArtifacts")
                        .get(0)
                        .get("uri")
                        .textValue());
        assertEquals(
                "script_output",
                json.get("artifacts").get(0).get("artifactType").textValue());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleBashJobValidationErrorReturnsFailedEvent() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("invalid-workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "script-test-worker-1");
        ScriptJob job = scriptJob(storageClientWithInput());
        JobMessage invalidJob = scriptJobMessage(Map.of(
                "script",
                "printf 'bad\\n'",
                "input_artifacts",
                List.of(Map.of("uri", INPUT_URI, "path", "../outside.txt"))));

        ExecutorEventMessage finishedEvent = handler.handle(invalidJob, job);

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertTrue(finishedEvent.summary().contains("input_artifacts.path"));
        assertTrue(logPublisher.events.isEmpty());
    }

    private ScriptJob scriptJob(LocalStorageClient storageClient) {
        return new ScriptJob(
                new ScriptWorkspacePreparer(storageClient),
                new ScriptRunner(new LocalProcessRunner()),
                new ScriptExpectedOutputResolver(),
                new ScriptOutputPublisher(storageClient));
    }

    private LocalStorageClient storageClientWithInput() throws Exception {
        Path storageRoot = tempDir.resolve("storage");
        Path sourcePath = tempDir.resolve("input.txt");
        Files.writeString(sourcePath, "payload from storage\n");
        LocalStorageClient storageClient = new LocalStorageClient(storageRoot);
        ArtifactDescriptor uploadedInput = storageClient
                .upload(new StorageUploadRequest(
                        sourcePath,
                        "script-inputs/%s/input.txt".formatted(JOB_EXECUTION_ID),
                        "script_input",
                        "input.txt",
                        "text/plain",
                        Map.of()))
                .toCompletableFuture()
                .join();
        assertEquals(INPUT_URI, uploadedInput.uri());
        return storageClient;
    }

    private JobMessage scriptJobMessage() {
        return scriptJobMessage(Map.of(
                "script",
                String.join(
                        System.lineSeparator(),
                        "set -euo pipefail",
                        "mkdir -p out",
                        "cat inputs/input.txt > out/result.txt",
                        "printf 'stdout: %s\\n' \"$GREETING\"",
                        "printf 'stderr: diagnostic\\n' >&2"),
                "input_artifacts",
                List.of(Map.of("uri", INPUT_URI, "path", "inputs/input.txt")),
                "environment",
                Map.of("GREETING", "hello from env"),
                "expected_outputs",
                List.of("out/*.txt")));
    }

    private JobMessage scriptJobMessage(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                UUID.fromString("00000000-0000-0000-0000-000000000904"),
                UUID.fromString("00000000-0000-0000-0000-000000000905"),
                UUID.fromString("00000000-0000-0000-0000-000000000906"),
                JOB_EXECUTION_ID,
                JobType.SCRIPT,
                "script/bash",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                null,
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T09:00:00Z"));
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
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
}
