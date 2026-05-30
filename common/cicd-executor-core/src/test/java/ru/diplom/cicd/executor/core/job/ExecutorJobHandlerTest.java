package ru.diplom.cicd.executor.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
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
import ru.diplom.cicd.executor.core.idempotency.IdempotencyClaim;
import ru.diplom.cicd.executor.core.idempotency.IdempotencyDecision;
import ru.diplom.cicd.executor.core.idempotency.IdempotencyDecisionType;
import ru.diplom.cicd.executor.core.idempotency.IdempotencyGuard;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;
import ru.diplom.cicd.executor.core.workspace.WorkspaceManager;

class ExecutorJobHandlerTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @TempDir
    private Path tempDir;

    @Test
    void handlePublishesRunningLogArtifactAndFinishedEvents() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingIdempotencyClaim idempotencyClaim = new CapturingIdempotencyClaim(IdempotencyDecision.started());
        ExecutorJobHandler handler =
                handler(workspaceManager, eventPublisher, logPublisher, new FixedIdempotencyGuard(idempotencyClaim));
        ArtifactDescriptor artifact = artifact();
        JobMessage job = job();

        ExecutorEventMessage finishedEvent = handler.handle(job, context -> {
            assertSame(job, context.job());
            assertEquals(JOB_EXECUTION_ID, context.workspace().jobExecutionId());
            return new ExecutorJobResult(
                    ExecutionStatus.SUCCESS,
                    "Сборка завершена успешно",
                    List.of(artifact),
                    Map.of("tests", 12),
                    "stdout token=super-secret",
                    null,
                    Map.of("template", "build/maven"));
        });

        assertEquals(3, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.get(0).eventType());
        assertEquals(ExecutionStatus.RUNNING, eventPublisher.events.get(0).status());
        assertEquals(EventType.JOB_ARTIFACT, eventPublisher.events.get(1).eventType());
        assertEquals(List.of(artifact), eventPublisher.events.get(1).artifacts());
        assertSame(finishedEvent, eventPublisher.events.get(2));
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals(List.of(artifact), finishedEvent.artifacts());
        assertEquals(Map.of("tests", 12), finishedEvent.metrics());
        assertEquals("Сборка завершена успешно", finishedEvent.summary());
        assertEquals("core-worker-1", finishedEvent.workerId());
        assertEquals(3L, finishedEvent.durationMs());
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertEquals("stdout token=[REDACTED]", logEvent.logs());
        assertEquals(true, logEvent.additionalData().get("logOnly"));

        assertTrue(workspaceManager.cleanupCalled);
        assertFalse(workspaceManager.cleanupFailed);
        assertSame(finishedEvent, idempotencyClaim.completedEvent);
        assertTrue(idempotencyClaim.closed);
    }

    @Test
    void handleSetsJobFieldsToMdcAndRestoresPreviousContext() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);

        MDC.put("requestId", "existing-request");
        try {
            ExecutorEventMessage finishedEvent = handler.handle(job(), context -> {
                assertEquals(JOB_EXECUTION_ID.toString(), MDC.get(ExecutorJobLoggingContext.JOB_EXECUTION_ID));
                assertEquals("00000000-0000-0000-0000-000000000002", MDC.get(ExecutorJobLoggingContext.CORRELATION_ID));
                assertEquals("existing-request", MDC.get("requestId"));
                return ExecutorJobResult.success("ok");
            });

            assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
            assertEquals("existing-request", MDC.get("requestId"));
            assertNull(MDC.get(ExecutorJobLoggingContext.JOB_EXECUTION_ID));
            assertNull(MDC.get(ExecutorJobLoggingContext.CORRELATION_ID));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void handlePublishesTypedFailureAndPreservesWorkspaceAsFailed() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);

        ExecutorEventMessage finishedEvent = handler.handle(job(), context -> {
            throw new ExecutorJobException(
                    ErrorType.USER_CODE_ERROR,
                    "build.maven.failed",
                    "Команда Maven завершилась с ошибкой",
                    "exitCode=1",
                    Map.of("exitCode", 1),
                    ExecutionStatus.FAILED);
        });

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.USER_CODE_ERROR, finishedEvent.error().type());
        assertEquals("build.maven.failed", finishedEvent.error().code());
        assertEquals("Команда Maven завершилась с ошибкой", finishedEvent.summary());
        assertEquals("exitCode=1", finishedEvent.error().details());
        assertEquals(1, finishedEvent.error().metadata().get("exitCode"));
        assertTrue(logPublisher.events.isEmpty());
        assertTrue(workspaceManager.cleanupCalled);
        assertTrue(workspaceManager.cleanupFailed);
    }

    @Test
    void handlePublishesValidationFailureWhenJobExecutionIdIsPresent() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);
        JobMessage invalidJob = job(2, JOB_EXECUTION_ID);

        ExecutorEventMessage finishedEvent = handler.handle(invalidJob, context -> ExecutorJobResult.success("ok"));

        assertEquals(1, eventPublisher.events.size());
        assertSame(finishedEvent, eventPublisher.events.getFirst());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertEquals("Неподдерживаемая версия job message: 2", finishedEvent.summary());
        assertFalse(workspaceManager.createCalled);
        assertFalse(workspaceManager.cleanupCalled);
    }

    @Test
    void handleRejectsUnsafeSandboxPolicyBeforeWorkspaceAndServiceCode() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);
        JobMessage unsafeJob = job(new SandboxPolicy(
                false,
                true,
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
                Map.of()));

        ExecutorEventMessage finishedEvent = handler.handle(unsafeJob, context -> {
            throw new AssertionError("Unsafe sandbox policy не должна выполнять service code");
        });

        assertEquals(1, eventPublisher.events.size());
        assertSame(finishedEvent, eventPublisher.events.getFirst());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.SECURITY_ERROR, finishedEvent.error().type());
        assertEquals("executor.sandbox.policy-denied", finishedEvent.error().code());
        assertEquals("Sandbox policy job нарушает требования безопасности", finishedEvent.summary());
        assertEquals("host network запрещен", finishedEvent.error().details());
        assertFalse(workspaceManager.createCalled);
        assertFalse(workspaceManager.cleanupCalled);
        assertTrue(logPublisher.events.isEmpty());
    }

    @Test
    void handleSkipsDuplicateCompletedJobWithoutExecutingServiceCode() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        CapturingIdempotencyClaim idempotencyClaim =
                new CapturingIdempotencyClaim(IdempotencyDecision.duplicateCompleted(
                        ExecutionStatus.SUCCESS,
                        "Сборка уже завершена успешно",
                        Instant.parse("2026-05-30T09:01:00Z"),
                        Map.of("previousMessageId", "00000000-0000-0000-0000-000000000201")));
        ExecutorJobHandler handler =
                handler(workspaceManager, eventPublisher, logPublisher, new FixedIdempotencyGuard(idempotencyClaim));

        ExecutorEventMessage skippedEvent = handler.handle(job(), context -> {
            throw new AssertionError("Duplicate job не должна выполнять service code");
        });

        assertEquals(1, eventPublisher.events.size());
        assertSame(skippedEvent, eventPublisher.events.getFirst());
        assertEquals(EventType.JOB_SKIPPED, skippedEvent.eventType());
        assertEquals(ExecutionStatus.SKIPPED, skippedEvent.status());
        assertEquals("Повторная доставка job пропущена: Сборка уже завершена успешно", skippedEvent.summary());
        assertEquals(true, skippedEvent.additionalData().get("idempotentDuplicate"));
        assertEquals(
                IdempotencyDecisionType.DUPLICATE_COMPLETED.name(),
                skippedEvent.additionalData().get("decisionType"));
        assertEquals(
                ExecutionStatus.SUCCESS.name(), skippedEvent.additionalData().get("previousStatus"));
        assertFalse(workspaceManager.createCalled);
        assertFalse(workspaceManager.cleanupCalled);
        assertTrue(logPublisher.events.isEmpty());
        assertNull(idempotencyClaim.completedEvent);
        assertTrue(idempotencyClaim.closed);
    }

    @Test
    void handleFailsFastWhenJobExecutionIdIsMissingBecauseKafkaKeyCannotBeBuilt() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);
        JobMessage invalidJob = job(1, null);

        ExecutorJobException error =
                assertThrows(ExecutorJobException.class, () -> handler.handle(invalidJob, context -> null));

        assertEquals(ErrorType.VALIDATION_ERROR, error.errorType());
        assertEquals("Не задан обязательный jobExecutionId", error.getMessage());
        assertTrue(eventPublisher.events.isEmpty());
        assertTrue(logPublisher.events.isEmpty());
        assertFalse(workspaceManager.createCalled);
    }

    @Test
    void handleMapsUnexpectedExceptionToUnknownError() {
        CapturingWorkspaceManager workspaceManager = new CapturingWorkspaceManager(tempDir);
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = handler(workspaceManager, eventPublisher, logPublisher);

        ExecutorEventMessage finishedEvent = handler.handle(job(), context -> {
            throw new IllegalStateException("boom");
        });

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.UNKNOWN, finishedEvent.error().type());
        assertEquals("executor.job.unexpected", finishedEvent.error().code());
        assertEquals("boom", finishedEvent.error().details());
        assertNotNull(finishedEvent.error().metadata().get("exceptionClass"));
        assertTrue(workspaceManager.cleanupFailed);
    }

    private ExecutorJobHandler handler(
            CapturingWorkspaceManager workspaceManager,
            CapturingEventPublisher eventPublisher,
            CapturingLogPublisher logPublisher) {
        return new ExecutorJobHandler(
                workspaceManager,
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                IdempotencyGuard.noop(),
                "core-worker-1",
                new TickClock(),
                new DeterministicUuidSupplier());
    }

    private ExecutorJobHandler handler(
            CapturingWorkspaceManager workspaceManager,
            CapturingEventPublisher eventPublisher,
            CapturingLogPublisher logPublisher,
            IdempotencyGuard idempotencyGuard) {
        return new ExecutorJobHandler(
                workspaceManager,
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                idempotencyGuard,
                "core-worker-1",
                new TickClock(),
                new DeterministicUuidSupplier());
    }

    private record FixedIdempotencyGuard(IdempotencyClaim claim) implements IdempotencyGuard {

        @Override
        public IdempotencyClaim acquire(JobMessage job) {
            return claim;
        }
    }

    private static final class CapturingIdempotencyClaim implements IdempotencyClaim {

        private final IdempotencyDecision decision;
        private ExecutorEventMessage completedEvent;
        private boolean closed;

        private CapturingIdempotencyClaim(IdempotencyDecision decision) {
            this.decision = decision;
        }

        @Override
        public IdempotencyDecision decision() {
            return decision;
        }

        @Override
        public void complete(ExecutorEventMessage event) {
            completedEvent = event;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private JobMessage job() {
        return job(1, JOB_EXECUTION_ID);
    }

    private JobMessage job(SandboxPolicy sandboxPolicy) {
        return job(1, JOB_EXECUTION_ID, sandboxPolicy);
    }

    private JobMessage job(int schemaVersion, UUID jobExecutionId) {
        return job(schemaVersion, jobExecutionId, safeSandboxPolicy());
    }

    private JobMessage job(int schemaVersion, UUID jobExecutionId, SandboxPolicy sandboxPolicy) {
        return new JobMessage(
                schemaVersion,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                jobExecutionId,
                JobType.BUILD,
                "build/maven",
                1,
                3,
                1800,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                sandboxPolicy,
                Map.of(),
                Map.of(),
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

    private ArtifactDescriptor artifact() {
        return new ArtifactDescriptor(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "build-artifact",
                "app.jar",
                "local://artifacts/app.jar",
                "application/java-archive",
                42L,
                "sha256",
                Map.of());
    }

    private static final class CapturingWorkspaceManager implements WorkspaceManager {

        private final Path root;
        private boolean createCalled;
        private boolean cleanupCalled;
        private boolean cleanupFailed;

        private CapturingWorkspaceManager(Path root) {
            this.root = root;
        }

        @Override
        public WorkspaceHandle create(UUID jobExecutionId, WorkspacePolicy policy) {
            createCalled = true;
            return new WorkspaceHandle(jobExecutionId, root.resolve(jobExecutionId.toString()), policy);
        }

        @Override
        public Path resolve(WorkspaceHandle workspace, String relativePath) {
            return workspace.root().resolve(relativePath);
        }

        @Override
        public boolean cleanup(WorkspaceHandle workspace, boolean failed) {
            cleanupCalled = true;
            cleanupFailed = failed;
            return true;
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
        public CompletionStage<Void> publish(ExecutorEventMessage logEvent) {
            events.add(logEvent);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class DeterministicUuidSupplier implements java.util.function.Supplier<UUID> {

        private final Queue<UUID> values = new ArrayDeque<>(List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                UUID.fromString("00000000-0000-0000-0000-000000000204")));

        @Override
        public UUID get() {
            return values.remove();
        }
    }

    private static final class TickClock extends Clock {

        private Instant instant = Instant.parse("2026-05-30T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant current = instant;
            instant = instant.plusMillis(1);
            return current;
        }
    }
}
