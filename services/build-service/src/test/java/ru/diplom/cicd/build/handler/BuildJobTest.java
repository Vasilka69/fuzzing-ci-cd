package ru.diplom.cicd.build.handler;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.build.artifact.ExpectedArtifactResolver;
import ru.diplom.cicd.build.runner.BuildRunner;
import ru.diplom.cicd.build.snapshot.BuildSourceSnapshotPreparer;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
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
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;

class BuildJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000307");
    private static final String SOURCE_SNAPSHOT_URI =
            "storage://source-snapshots/00000000-0000-0000-0000-000000000307/source-snapshot.tar.gz";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleMavenJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        StorageClient storageClient = storageClientWithSourceSnapshot();
        SnapshotAwareProcessRunner processRunner = new SnapshotAwareProcessRunner(processResult(0, "maven ok\n", ""));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "build-test-worker-1");
        BuildJob job = new BuildJob(
                new BuildRunner(processRunner),
                new BuildSourceSnapshotPreparer(storageClient, processRunner),
                new ExpectedArtifactResolver());

        ExecutorEventMessage finishedEvent = handler.handle(buildJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Сборка maven завершена успешно", finishedEvent.summary());
        assertEquals("maven", finishedEvent.additionalData().get("buildTool"));
        assertEquals(SOURCE_SNAPSHOT_URI, finishedEvent.additionalData().get("sourceSnapshotUri"));
        assertEquals("source", finishedEvent.additionalData().get("sourceDirectory"));
        assertEquals(".", finishedEvent.additionalData().get("workingDirectory"));
        assertEquals("./mvnw", finishedEvent.additionalData().get("entrypoint"));
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertEquals(List.of("target/*.jar"), finishedEvent.additionalData().get("expectedArtifactPatterns"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expectedArtifacts =
                (List<Map<String, Object>>) finishedEvent.additionalData().get("expectedArtifacts");
        assertEquals(1, expectedArtifacts.size());
        assertEquals("target/*.jar", expectedArtifacts.getFirst().get("pattern"));
        assertEquals("target/app.jar", expectedArtifacts.getFirst().get("path"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Source snapshot скачан из storage"));
        assertTrue(logEvent.logs().contains("Source snapshot tar.gz распакован"));
        assertTrue(logEvent.logs().contains("Сборка maven завершена успешно"));
        assertTrue(logEvent.logs().contains("maven ok"));

        assertEquals(List.of("./mvnw", "-q", "test"), processRunner.buildRequest.command());
        assertEquals(
                "source",
                processRunner.buildRequest.workingDirectory().getFileName().toString());
        assertTrue(processRunner.buildRequest.workingDirectory().startsWith(tempDir.resolve("workspaces")));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("build", json.get("jobType").textValue());
        assertEquals("build/maven", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals("maven", json.get("additionalData").get("buildTool").textValue());
        assertEquals(
                SOURCE_SNAPSHOT_URI,
                json.get("additionalData").get("sourceSnapshotUri").textValue());
        assertEquals("source", json.get("additionalData").get("sourceDirectory").textValue());
        assertEquals("./mvnw", json.get("additionalData").get("entrypoint").textValue());
        assertEquals(
                "target/*.jar",
                json.get("additionalData")
                        .get("expectedArtifactPatterns")
                        .get(0)
                        .textValue());
        assertEquals(
                "target/app.jar",
                json.get("additionalData")
                        .get("expectedArtifacts")
                        .get(0)
                        .get("path")
                        .textValue());
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
                Map.of(
                        "build_tool",
                        "maven",
                        "source_snapshot_uri",
                        SOURCE_SNAPSHOT_URI,
                        "entrypoint",
                        "./mvnw",
                        "args",
                        List.of("-q", "test"),
                        "expected_artifacts",
                        List.of("target/*.jar")),
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

    private StorageClient storageClientWithSourceSnapshot() throws Exception {
        Path storageRoot = tempDir.resolve("storage");
        Path sourceDirectory = tempDir.resolve("source-project");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("mvnw"), "#!/usr/bin/env sh\nprintf 'maven ok\\n'\n");
        Path archivePath = tempDir.resolve("source-snapshot.tar.gz");
        tar(sourceDirectory, "-czf", archivePath.toString(), "-C", sourceDirectory.toString(), ".");
        LocalStorageClient storageClient = new LocalStorageClient(storageRoot);
        ArtifactDescriptor uploadedSnapshot = storageClient
                .upload(new StorageUploadRequest(
                        archivePath,
                        "source-snapshots/%s/source-snapshot.tar.gz".formatted(JOB_EXECUTION_ID),
                        "source_snapshot",
                        "source-snapshot.tar.gz",
                        "application/gzip",
                        Map.of()))
                .toCompletableFuture()
                .join();
        assertEquals(SOURCE_SNAPSHOT_URI, uploadedSnapshot.uri());
        return storageClient;
    }

    private void tar(Path workingDirectory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("tar");
        command.addAll(List.of(args));
        Process process =
                new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Tar test command failed: " + command + System.lineSeparator() + stderr);
        }
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

    private static final class SnapshotAwareProcessRunner implements ProcessRunner {

        private final LocalProcessRunner localProcessRunner = new LocalProcessRunner();
        private final ProcessExecutionResult result;
        private ProcessExecutionRequest buildRequest;

        private SnapshotAwareProcessRunner(ProcessExecutionResult result) {
            this.result = result;
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            if (!request.command().isEmpty() && "tar".equals(request.command().getFirst())) {
                return localProcessRunner.run(request);
            }
            this.buildRequest = request;
            createBuildArtifact(request.workingDirectory());
            return result;
        }

        private void createBuildArtifact(Path workingDirectory) {
            try {
                Files.createDirectories(workingDirectory.resolve("target"));
                Files.writeString(workingDirectory.resolve("target/app.jar"), "jar");
            } catch (Exception exception) {
                throw new AssertionError("Не удалось подготовить тестовый build artifact", exception);
            }
        }
    }
}
