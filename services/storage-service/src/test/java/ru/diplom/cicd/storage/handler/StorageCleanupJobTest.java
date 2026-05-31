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
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.storage.backend.LocalFilesystemStorageBackend;

class StorageCleanupJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000307");
    private static final String STORAGE_URI =
            "storage://temporary/00000000-0000-0000-0000-000000000307/source-snapshot.tmp";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleCleanupJobDeletesTemporaryArtifactAndPublishesContractResult() throws Exception {
        Path storageRoot = tempDir.resolve("storage");
        Path temporaryArtifact =
                storageRoot.resolve("temporary/00000000-0000-0000-0000-000000000307/source-snapshot.tmp");
        Files.createDirectories(temporaryArtifact.getParent());
        Files.writeString(temporaryArtifact, "temporary payload");
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "storage-test-worker-1");
        StorageCleanupJob job = new StorageCleanupJob(new LocalFilesystemStorageBackend(storageRoot));

        ExecutorEventMessage finishedEvent = handler.handle(storageCleanupJob(STORAGE_URI, false), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Временные artifacts удалены из локального хранилища", finishedEvent.summary());
        assertEquals("cleanup", finishedEvent.additionalData().get("operation"));
        assertEquals(STORAGE_URI, finishedEvent.additionalData().get("storageUri"));
        assertEquals(
                "temporary/00000000-0000-0000-0000-000000000307/source-snapshot.tmp",
                finishedEvent.additionalData().get("namespacePath"));
        assertEquals(false, finishedEvent.additionalData().get("recursive"));
        assertEquals(true, finishedEvent.additionalData().get("deleted"));
        assertEquals(1L, finishedEvent.additionalData().get("deletedCount"));
        assertEquals(17L, finishedEvent.additionalData().get("bytesFreed"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertTrue(Files.notExists(temporaryArtifact));
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Local filesystem storage cleanup обработал namespace"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("storage", json.get("jobType").textValue());
        assertEquals("storage/cleanup", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(0, json.get("artifacts").size());
        assertEquals("cleanup", json.get("additionalData").get("operation").textValue());
        assertEquals(STORAGE_URI, json.get("additionalData").get("storageUri").textValue());
        assertTrue(json.get("additionalData").get("deleted").booleanValue());
        assertEquals(1L, json.get("additionalData").get("deletedCount").longValue());
        assertEquals(17L, json.get("additionalData").get("bytesFreed").longValue());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleCleanupJobRejectsDirectoryWithoutRecursiveFlagAsValidationError() throws Exception {
        Path namespace = tempDir.resolve("storage/temporary/00000000-0000-0000-0000-000000000307");
        Files.createDirectories(namespace);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "storage-test-worker-1");
        StorageCleanupJob job = new StorageCleanupJob(new LocalFilesystemStorageBackend(tempDir.resolve("storage")));

        ExecutorEventMessage finishedEvent = handler.handle(
                storageCleanupJob("storage://temporary/00000000-0000-0000-0000-000000000307", false), job);

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertNotNull(finishedEvent.error());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertEquals("executor.job.validation", finishedEvent.error().code());
        assertTrue(finishedEvent.summary().contains("для удаления дерева нужен recursive=true"));
        assertTrue(logPublisher.events.isEmpty());
        assertTrue(Files.isDirectory(namespace));
    }

    private JobMessage storageCleanupJob(String storageUri, boolean recursive) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                UUID.fromString("00000000-0000-0000-0000-000000000303"),
                UUID.fromString("00000000-0000-0000-0000-000000000304"),
                UUID.fromString("00000000-0000-0000-0000-000000000305"),
                UUID.fromString("00000000-0000-0000-0000-000000000306"),
                JOB_EXECUTION_ID,
                JobType.STORAGE,
                "storage/cleanup",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of("operation", "cleanup", "storageUri", storageUri, "recursive", recursive),
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
