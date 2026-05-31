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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.build.artifact.BuildArtifactBundlePublisher;
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
                new ExpectedArtifactResolver(),
                new BuildArtifactBundlePublisher(storageClient, processRunner, objectMapper()));

        ExecutorEventMessage finishedEvent = handler.handle(buildJob(), job);

        assertEquals(3, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        ExecutorEventMessage artifactEvent = eventPublisher.events.get(1);
        assertEquals(EventType.JOB_ARTIFACT, artifactEvent.eventType());
        assertEquals(ExecutionStatus.RUNNING, artifactEvent.status());
        assertEquals(1, artifactEvent.artifacts().size());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Сборка maven завершена успешно", finishedEvent.summary());
        assertEquals("maven", finishedEvent.additionalData().get("buildTool"));
        assertEquals(SOURCE_SNAPSHOT_URI, finishedEvent.additionalData().get("sourceSnapshotUri"));
        assertEquals("source", finishedEvent.additionalData().get("sourceDirectory"));
        assertEquals(".", finishedEvent.additionalData().get("workingDirectory"));
        assertEquals("./mvnw", finishedEvent.additionalData().get("entrypoint"));
        assertEquals(0, finishedEvent.additionalData().get("exitCode"));
        assertEquals(
                BuildRunner.MAX_OUTPUT_BYTES_PER_STREAM,
                finishedEvent.additionalData().get("outputLimitBytesPerStream"));
        assertEquals(false, finishedEvent.additionalData().get("stdoutTruncated"));
        assertEquals(false, finishedEvent.additionalData().get("stderrTruncated"));
        assertEquals(List.of("target/*.jar"), finishedEvent.additionalData().get("expectedArtifactPatterns"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expectedArtifacts =
                (List<Map<String, Object>>) finishedEvent.additionalData().get("expectedArtifacts");
        assertEquals(1, expectedArtifacts.size());
        assertEquals("target/*.jar", expectedArtifacts.getFirst().get("pattern"));
        assertEquals("target/app.jar", expectedArtifacts.getFirst().get("path"));
        @SuppressWarnings("unchecked")
        Map<String, Object> buildArtifactsBundle =
                (Map<String, Object>) finishedEvent.additionalData().get("buildArtifactsBundle");
        assertEquals(
                "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(JOB_EXECUTION_ID),
                buildArtifactsBundle.get("uri"));
        assertEquals("build-artifacts.tar.gz", buildArtifactsBundle.get("fileName"));
        assertEquals("tar.gz", buildArtifactsBundle.get("format"));
        assertEquals("application/gzip", buildArtifactsBundle.get("contentType"));
        assertEquals(1, buildArtifactsBundle.get("artifactCount"));
        assertEquals("artifact-manifest.json", buildArtifactsBundle.get("manifestPath"));
        assertEquals(1, finishedEvent.artifacts().size());
        ArtifactDescriptor buildArtifacts = finishedEvent.artifacts().getFirst();
        assertEquals("build_artifacts", buildArtifacts.artifactType());
        assertEquals("build-artifacts.tar.gz", buildArtifacts.name());
        assertEquals("application/gzip", buildArtifacts.contentType());
        assertEquals(
                "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(JOB_EXECUTION_ID),
                buildArtifacts.uri());
        assertEquals(artifactEvent.artifacts(), finishedEvent.artifacts());
        assertNull(finishedEvent.logs());

        Path bundlePath =
                tempDir.resolve("storage/build-artifacts/%s/build-artifacts.tar.gz".formatted(JOB_EXECUTION_ID));
        assertTrue(Files.exists(bundlePath));
        assertArchiveContains(bundlePath, "./artifact-manifest.json", "./artifacts/target/app.jar");
        Path extractedBundle = tempDir.resolve("extracted-build-artifacts");
        Files.createDirectories(extractedBundle);
        tar(tempDir, "-xzf", bundlePath.toString(), "-C", extractedBundle.toString());
        JsonNode manifest = objectMapper()
                .readTree(extractedBundle.resolve("artifact-manifest.json").toFile());
        assertEquals("build_artifacts", manifest.get("artifactType").textValue());
        assertEquals("tar.gz", manifest.get("archiveFormat").textValue());
        assertEquals(JOB_EXECUTION_ID.toString(), manifest.get("jobExecutionId").textValue());
        assertEquals(
                "target/*.jar", manifest.get("expectedArtifactPatterns").get(0).textValue());
        assertEquals("target/app.jar", manifest.get("files").get(0).get("path").textValue());
        assertEquals(
                "artifacts/target/app.jar",
                manifest.get("files").get(0).get("archivePath").textValue());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Source snapshot скачан из storage"));
        assertTrue(logEvent.logs().contains("Source snapshot tar.gz распакован"));
        assertTrue(logEvent.logs().contains("Сборка maven завершена успешно"));
        assertTrue(logEvent.logs().contains("maven ok"));
        assertTrue(logEvent.logs().contains("Build artifacts tar.gz опубликован"));

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
                BuildRunner.MAX_OUTPUT_BYTES_PER_STREAM,
                json.get("additionalData").get("outputLimitBytesPerStream").intValue());
        assertFalse(json.get("additionalData").get("stdoutTruncated").booleanValue());
        assertFalse(json.get("additionalData").get("stderrTruncated").booleanValue());
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
        assertEquals(
                "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(JOB_EXECUTION_ID),
                json.get("additionalData")
                        .get("buildArtifactsBundle")
                        .get("uri")
                        .textValue());
        assertEquals(
                "build_artifacts",
                json.get("artifacts").get(0).get("artifactType").textValue());
        assertEquals(
                "build-artifacts.tar.gz",
                json.get("artifacts").get(0).get("name").textValue());
        assertEquals(
                "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(JOB_EXECUTION_ID),
                json.get("artifacts").get(0).get("uri").textValue());
        assertEquals(1, json.get("artifacts").size());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleMavenJobPublishesTruncatedOutputMetadataAndJobLogMarker() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        StorageClient storageClient = storageClientWithSourceSnapshot();
        SnapshotAwareProcessRunner processRunner =
                new SnapshotAwareProcessRunner(processResult(0, "abcde", "", Set.of(ProcessStreamType.STDOUT)));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "build-test-worker-1");
        BuildJob job = new BuildJob(
                new BuildRunner(processRunner),
                new BuildSourceSnapshotPreparer(storageClient, processRunner),
                new ExpectedArtifactResolver(),
                new BuildArtifactBundlePublisher(storageClient, processRunner, objectMapper()));

        ExecutorEventMessage finishedEvent = handler.handle(buildJob(), job);

        assertNull(finishedEvent.logs());
        assertEquals(true, finishedEvent.additionalData().get("stdoutTruncated"));
        assertEquals(false, finishedEvent.additionalData().get("stderrTruncated"));
        assertEquals(
                BuildRunner.MAX_OUTPUT_BYTES_PER_STREAM,
                finishedEvent.additionalData().get("outputLimitBytesPerStream"));
        assertEquals(1, logPublisher.events.size());
        assertTrue(logPublisher.events.getFirst().logs().contains("[stdout усечен: сохранено не более 65536 байт]"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertTrue(json.get("additionalData").get("stdoutTruncated").booleanValue());
        assertFalse(json.get("additionalData").get("stderrTruncated").booleanValue());
        assertEquals(
                BuildRunner.MAX_OUTPUT_BYTES_PER_STREAM,
                json.get("additionalData").get("outputLimitBytesPerStream").intValue());
        assertTrue(json.get("logs").isNull());
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

    private void assertArchiveContains(Path archivePath, String... expectedEntries) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("tar");
        command.add("-tzf");
        command.add(archivePath.toString());
        Process process =
                new ProcessBuilder(command).directory(tempDir.toFile()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Tar test command failed: " + command + System.lineSeparator() + stderr);
        }
        for (String expectedEntry : expectedEntries) {
            assertTrue(stdout.contains(expectedEntry), "Archive does not contain " + expectedEntry);
        }
    }

    private static ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return processResult(exitCode, stdout, stderr, Set.of());
    }

    private static ProcessExecutionResult processResult(
            int exitCode, String stdout, String stderr, Set<ProcessStreamType> truncatedStreams) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(
                        processChunk(ProcessStreamType.STDOUT, 0, stdout),
                        processChunk(ProcessStreamType.STDERR, 1, stderr)),
                truncatedStreams);
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
