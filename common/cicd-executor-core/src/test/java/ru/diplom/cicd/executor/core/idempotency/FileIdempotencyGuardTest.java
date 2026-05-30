package ru.diplom.cicd.executor.core.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;

class FileIdempotencyGuardTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final Instant NOW = Instant.parse("2026-05-30T09:00:00Z");

    @TempDir
    private Path tempDir;

    @Test
    void acquireCreatesMarkerAndSkipsDuplicateAfterSuccess() {
        FileIdempotencyGuard guard = guard();
        JobMessage job = job();

        IdempotencyClaim firstClaim = guard.acquire(job);
        assertTrue(firstClaim.shouldExecute());
        firstClaim.complete(event(job, ExecutionStatus.SUCCESS, "Сборка завершена успешно"));
        firstClaim.close();

        IdempotencyClaim duplicateClaim = guard.acquire(job);

        assertFalse(duplicateClaim.shouldExecute());
        assertEquals(
                IdempotencyDecisionType.DUPLICATE_COMPLETED,
                duplicateClaim.decision().type());
        assertEquals(ExecutionStatus.SUCCESS, duplicateClaim.decision().previousStatus());
        assertEquals(
                "Повторная доставка job пропущена: Сборка завершена успешно",
                duplicateClaim.decision().summary());
        assertEquals(
                EventType.JOB_FINISHED.name(),
                duplicateClaim.decision().metadata().get("eventType"));
        duplicateClaim.close();
    }

    @Test
    void acquireSkipsConcurrentDuplicateWhileLockIsHeld() {
        FileIdempotencyGuard guard = guard();
        JobMessage job = job();

        IdempotencyClaim firstClaim = guard.acquire(job);
        IdempotencyClaim duplicateClaim = guard.acquire(job);

        assertTrue(firstClaim.shouldExecute());
        assertFalse(duplicateClaim.shouldExecute());
        assertEquals(
                IdempotencyDecisionType.DUPLICATE_RUNNING,
                duplicateClaim.decision().type());
        assertEquals(ExecutionStatus.RUNNING, duplicateClaim.decision().previousStatus());

        duplicateClaim.close();
        firstClaim.close();
    }

    @Test
    void acquireAllowsRetryAfterFailedResult() {
        FileIdempotencyGuard guard = guard();
        JobMessage job = job();

        IdempotencyClaim failedClaim = guard.acquire(job);
        failedClaim.complete(event(job, ExecutionStatus.FAILED, "Сборка завершилась с ошибкой"));
        failedClaim.close();

        IdempotencyClaim retryClaim = guard.acquire(job);

        assertTrue(retryClaim.shouldExecute());
        retryClaim.close();
    }

    @Test
    void acquireReportsCorruptedStateFile() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(JOB_EXECUTION_ID + ".state.json"), "{broken-json");
        FileIdempotencyGuard guard = guard();

        JobMessage job = job();
        IdempotencyException error = assertThrows(IdempotencyException.class, () -> guard.acquire(job));

        assertEquals("Не удалось проверить idempotency marker job: " + JOB_EXECUTION_ID, error.getMessage());
    }

    private FileIdempotencyGuard guard() {
        return new FileIdempotencyGuard(tempDir, new ObjectMapper(), Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private JobMessage job() {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                JOB_EXECUTION_ID,
                JobType.BUILD,
                "build/maven",
                1,
                3,
                1800,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                new SandboxPolicy(
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
                        Map.of()),
                Map.of(),
                Map.of(),
                Map.of("refs", List.of()),
                NOW);
    }

    private ExecutorEventMessage event(JobMessage job, ExecutionStatus status, String summary) {
        return new ExecutorEventMessage(
                job.schemaVersion(),
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                job.correlationId(),
                job.pipelineRunId(),
                job.pipelineId(),
                job.stageId(),
                job.jobId(),
                job.jobExecutionId(),
                job.jobType(),
                job.templatePath(),
                EventType.JOB_FINISHED,
                status,
                job.attempt(),
                "core-worker-1",
                10L,
                List.of(),
                Map.of(),
                summary,
                null,
                null,
                Map.of());
    }
}
