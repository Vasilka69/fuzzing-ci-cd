package ru.diplom.cicd.deploy.handler;

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
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;

class DeployJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000707/app.jar";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleFileCopyJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        LocalStorageClient storageClient = storageClientWithArtifact();
        Path targetRoot = tempDir.resolve("deploy-target");
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "deploy-test-worker-1");
        DeployJob job = new DeployJob(new FileCopyDeploymentRunner(storageClient, targetRoot));

        ExecutorEventMessage finishedEvent = handler.handle(deployJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Deploy file-copy завершен успешно", finishedEvent.summary());
        assertEquals("file_copy", finishedEvent.additionalData().get("deploymentType"));
        assertEquals(ARTIFACT_URI, finishedEvent.additionalData().get("artifactUri"));
        assertEquals("testing", finishedEvent.additionalData().get("environment"));
        assertEquals("apps/app.jar", finishedEvent.additionalData().get("relativeDestinationPath"));
        assertEquals("release-2026-05-31-001", finishedEvent.additionalData().get("releaseId"));
        assertEquals("local-demo-target", finishedEvent.additionalData().get("connectionRef"));
        assertEquals(14L, finishedEvent.additionalData().get("bytesCopied"));
        assertEquals(true, finishedEvent.additionalData().get("checksumVerified"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        Path deployedArtifact = targetRoot.resolve("apps/app.jar");
        assertTrue(Files.isRegularFile(deployedArtifact));
        assertEquals("deployable jar", Files.readString(deployedArtifact));
        String checksum = StorageChecksums.sha256(deployedArtifact);
        assertEquals(checksum, finishedEvent.additionalData().get("deployedArtifactChecksum"));

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Deploy file-copy скачал artifact из storage"));
        assertTrue(logEvent.logs().contains("Deploy file-copy скопировал artifact в target path"));
        assertTrue(logEvent.logs().contains(checksum));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("deploy", json.get("jobType").textValue());
        assertEquals("deploy/file-copy", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(
                "file_copy", json.get("additionalData").get("deploymentType").textValue());
        assertEquals(ARTIFACT_URI, json.get("additionalData").get("artifactUri").textValue());
        assertEquals("testing", json.get("additionalData").get("environment").textValue());
        assertEquals(
                "apps/app.jar",
                json.get("additionalData").get("relativeDestinationPath").textValue());
        assertEquals(14L, json.get("additionalData").get("bytesCopied").longValue());
        assertEquals(
                checksum,
                json.get("additionalData").get("deployedArtifactChecksum").textValue());
        assertTrue(json.get("additionalData").get("checksumVerified").booleanValue());
        assertEquals(
                "release-2026-05-31-001",
                json.get("additionalData").get("releaseId").textValue());
        assertEquals(
                "local-demo-target",
                json.get("additionalData").get("connectionRef").textValue());
        assertEquals(0, json.get("artifacts").size());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleFileCopyValidationErrorReturnsFailedEvent() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        DeployJob job =
                new DeployJob(new FileCopyDeploymentRunner(storageClientWithArtifact(), tempDir.resolve("target")));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "deploy-test-worker-1");

        ExecutorEventMessage finishedEvent = handler.handle(
                deployJob(Map.of(
                        "artifact_uri", ARTIFACT_URI, "target", Map.of("destination_path", "../outside/app.jar"))),
                job);

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertTrue(finishedEvent.summary().contains("target.destination_path"));
        assertTrue(logPublisher.events.isEmpty());
    }

    private LocalStorageClient storageClientWithArtifact() throws Exception {
        LocalStorageClient storageClient = new LocalStorageClient(tempDir.resolve("storage"));
        Path artifact = tempDir.resolve("app.jar");
        Files.writeString(artifact, "deployable jar");
        storageClient
                .upload(new StorageUploadRequest(
                        artifact,
                        "release-artifacts/%s/app.jar".formatted(JOB_EXECUTION_ID),
                        "release_artifact",
                        "app.jar",
                        "application/java-archive",
                        Map.of()))
                .toCompletableFuture()
                .join();
        return storageClient;
    }

    private JobMessage deployJob() {
        return deployJob(Map.of(
                "deployment_type",
                "file_copy",
                "artifact_uri",
                ARTIFACT_URI,
                "environment",
                "testing",
                "target",
                Map.of("connection_ref", "local-demo-target", "destination_path", "apps/app.jar"),
                "verify_checksum",
                true,
                "release_id",
                "release-2026-05-31-001"));
    }

    private JobMessage deployJob(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                UUID.fromString("00000000-0000-0000-0000-000000000703"),
                UUID.fromString("00000000-0000-0000-0000-000000000704"),
                UUID.fromString("00000000-0000-0000-0000-000000000705"),
                UUID.fromString("00000000-0000-0000-0000-000000000706"),
                JOB_EXECUTION_ID,
                JobType.DEPLOY,
                FileCopyDeploymentParameters.TEMPLATE_PATH,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T11:00:00Z"));
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
