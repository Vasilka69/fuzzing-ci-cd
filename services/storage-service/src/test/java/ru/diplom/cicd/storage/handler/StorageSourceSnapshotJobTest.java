package ru.diplom.cicd.storage.handler;

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
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.StorageUris;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.storage.backend.LocalFilesystemStorageBackend;

class StorageSourceSnapshotJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final String STORAGE_PAYLOAD_SHA256 =
            "5e1c7766758f09dc15399c4c444a9c5734cf49bda9797f31c2f42af3be2fbbaa";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleSourceSnapshotJobPublishesArtifactAndFinishedEventWithoutInlineLogs() throws Exception {
        Path sourceSnapshot = tempDir.resolve("source-snapshot.tar.gz");
        Files.writeString(sourceSnapshot, "storage payload");
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "storage-test-worker-1");
        StorageSourceSnapshotJob job =
                new StorageSourceSnapshotJob(new LocalFilesystemStorageBackend(tempDir.resolve("storage")));

        ExecutorEventMessage finishedEvent = handler.handle(storageJob(sourceSnapshot), job);

        assertEquals(3, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_ARTIFACT, eventPublisher.events.get(1).eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Source snapshot сохранен в локальное хранилище", finishedEvent.summary());
        assertEquals(
                "storage://source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz",
                finishedEvent.additionalData().get("storageUri"));
        assertEquals(STORAGE_PAYLOAD_SHA256, finishedEvent.additionalData().get("checksumSha256"));
        assertEquals(15L, finishedEvent.additionalData().get("sizeBytes"));
        assertEquals("application/gzip", finishedEvent.additionalData().get("contentType"));
        assertEquals(1, finishedEvent.artifacts().size());

        ArtifactDescriptor artifact = finishedEvent.artifacts().getFirst();
        assertEquals("source_snapshot", artifact.artifactType());
        assertEquals("source-snapshot.tar.gz", artifact.name());
        assertEquals(finishedEvent.additionalData().get("storageUri"), artifact.uri());
        assertEquals(
                "source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz",
                StorageUris.namespacePath(artifact.uri()));
        assertEquals("application/gzip", artifact.contentType());
        assertEquals(15L, artifact.sizeBytes());
        assertEquals(STORAGE_PAYLOAD_SHA256, artifact.checksumSha256());
        assertEquals("git", artifact.metadata().get("vcsType"));
        assertEquals(JOB_EXECUTION_ID, artifact.metadata().get("jobExecutionId"));
        assertTrue(Files.isRegularFile(tempDir.resolve(
                "storage/source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz")));
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Local filesystem storage сохранил артефакт"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("storage", json.get("jobType").textValue());
        assertEquals("storage/source-snapshot", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(1, json.get("artifacts").size());
        assertEquals(
                "storage://source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz",
                json.get("artifacts").get(0).get("uri").textValue());
        assertEquals(
                "source_snapshot",
                json.get("artifacts").get(0).get("artifactType").textValue());
        assertEquals(
                "storage://source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz",
                json.get("additionalData").get("storageUri").textValue());
        assertEquals(15L, json.get("additionalData").get("sizeBytes").longValue());
        assertEquals(
                STORAGE_PAYLOAD_SHA256,
                json.get("additionalData").get("checksumSha256").textValue());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleSourceSnapshotJobRejectsChecksumMismatchAsValidationError() throws Exception {
        Path sourceSnapshot = tempDir.resolve("source-snapshot.tar.gz");
        Files.writeString(sourceSnapshot, "storage payload");
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "storage-test-worker-1");
        StorageSourceSnapshotJob job =
                new StorageSourceSnapshotJob(new LocalFilesystemStorageBackend(tempDir.resolve("storage")));

        ExecutorEventMessage finishedEvent = handler.handle(
                storageJob(sourceSnapshot, "0000000000000000000000000000000000000000000000000000000000000000"), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertNotNull(finishedEvent.error());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertEquals("executor.job.validation", finishedEvent.error().code());
        assertTrue(finishedEvent.summary().startsWith("SHA-256 checksum артефакта не совпадает для storage://"));
        assertTrue(logPublisher.events.isEmpty());
        assertTrue(Files.notExists(tempDir.resolve(
                "storage/source-snapshots/00000000-0000-0000-0000-000000000207/source-snapshot.tar.gz")));
    }

    private JobMessage storageJob(Path sourceSnapshot) {
        return storageJob(sourceSnapshot, STORAGE_PAYLOAD_SHA256);
    }

    private JobMessage storageJob(Path sourceSnapshot, String expectedChecksumSha256) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                UUID.fromString("00000000-0000-0000-0000-000000000206"),
                JOB_EXECUTION_ID,
                JobType.STORAGE,
                "storage/source-snapshot",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of(
                        "operation",
                        "save",
                        "sourceUri",
                        sourceSnapshot.toUri().toString(),
                        "destinationPath",
                        "source-snapshots/%s/source-snapshot.tar.gz".formatted(JOB_EXECUTION_ID),
                        "artifactType",
                        "source_snapshot",
                        "name",
                        "source-snapshot.tar.gz",
                        "contentType",
                        "application/gzip",
                        "checksumSha256",
                        expectedChecksumSha256,
                        "metadata",
                        Map.of("vcsType", "git")),
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
