package ru.diplom.cicd.executor.core.job;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.error.ExecutorError;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;
import ru.diplom.cicd.executor.core.workspace.WorkspaceManager;

/**
 * Общий runtime pipeline executor-а: валидация, workspace, события, отдельные log-документы и cleanup.
 */
public final class ExecutorJobHandler {

    private final WorkspaceManager workspaceManager;
    private final ExecutorEventPublisher eventPublisher;
    private final ExecutorLogPublisher logPublisher;
    private final SecretRedactor secretRedactor;
    private final String workerId;
    private final Clock clock;
    private final Supplier<UUID> messageIdSupplier;

    public ExecutorJobHandler(
            WorkspaceManager workspaceManager,
            ExecutorEventPublisher eventPublisher,
            ExecutorLogPublisher logPublisher,
            SecretRedactor secretRedactor,
            String workerId) {
        this(
                workspaceManager,
                eventPublisher,
                logPublisher,
                secretRedactor,
                workerId,
                Clock.systemUTC(),
                UUID::randomUUID);
    }

    ExecutorJobHandler(
            WorkspaceManager workspaceManager,
            ExecutorEventPublisher eventPublisher,
            ExecutorLogPublisher logPublisher,
            SecretRedactor secretRedactor,
            String workerId,
            Clock clock,
            Supplier<UUID> messageIdSupplier) {
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.logPublisher = Objects.requireNonNull(logPublisher, "logPublisher");
        this.secretRedactor = Objects.requireNonNull(secretRedactor, "secretRedactor");
        this.workerId = workerId == null || workerId.isBlank() ? "executor-worker" : workerId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.messageIdSupplier = Objects.requireNonNull(messageIdSupplier, "messageIdSupplier");
    }

    public ExecutorEventMessage handle(JobMessage job, ExecutorJob executorJob) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(executorJob, "executorJob");

        Instant startedAt = clock.instant();
        WorkspaceHandle workspace = null;
        ExecutionStatus cleanupStatus = ExecutionStatus.FAILED;

        try {
            validate(job);
            workspace = workspaceManager.create(job.jobExecutionId(), job.workspacePolicy());
            publishEvent(event(
                    job,
                    EventType.JOB_RUNNING,
                    ExecutionStatus.RUNNING,
                    null,
                    List.of(),
                    Map.of(),
                    "Job принят executor-ом и запущен",
                    null,
                    null,
                    Map.of()));

            ExecutorJobResult result = requireResult(executorJob.execute(new ExecutorJobContext(job, workspace)));
            publishLogs(job, result, startedAt);
            publishArtifacts(job, result, startedAt);

            ExecutorEventMessage finishedEvent = event(
                    job,
                    EventType.JOB_FINISHED,
                    result.status(),
                    resultDuration(startedAt),
                    result.artifacts(),
                    result.metrics(),
                    result.summary(),
                    result.error(),
                    null,
                    result.additionalData());
            publishEvent(finishedEvent);
            cleanupStatus = result.status();
            return finishedEvent;
        } catch (ExecutorJobException exception) {
            if (job.jobExecutionId() == null) {
                throw exception;
            }
            ExecutorEventMessage failedEvent = failedEvent(job, startedAt, exception);
            publishEvent(failedEvent);
            cleanupStatus = exception.status();
            return failedEvent;
        } catch (Exception exception) {
            ExecutorJobException typedException = new ExecutorJobException(
                    ErrorType.UNKNOWN,
                    "executor.job.unexpected",
                    "Executor завершил job с непредвиденной ошибкой",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
            ExecutorEventMessage failedEvent = failedEvent(job, startedAt, typedException);
            publishEvent(failedEvent);
            return failedEvent;
        } finally {
            if (workspace != null) {
                workspaceManager.cleanup(workspace, shouldPreserveAsFailure(cleanupStatus));
            }
        }
    }

    private void validate(JobMessage job) {
        if (job.schemaVersion() != 1) {
            throw ExecutorJobException.validation("Неподдерживаемая версия job message: " + job.schemaVersion());
        }
        if (job.jobExecutionId() == null) {
            throw ExecutorJobException.validation("Не задан обязательный jobExecutionId");
        }
        if (job.jobType() == null) {
            throw ExecutorJobException.validation("Не задан обязательный jobType");
        }
        if (job.templatePath() == null || job.templatePath().isBlank()) {
            throw ExecutorJobException.validation("Не задан обязательный templatePath");
        }
        if (job.attempt() < 1) {
            throw ExecutorJobException.validation("Номер попытки job должен быть положительным");
        }
        if (job.timeoutSeconds() < 1) {
            throw ExecutorJobException.validation("Timeout job должен быть положительным");
        }
    }

    private ExecutorJobResult requireResult(ExecutorJobResult result) {
        if (result == null) {
            throw new ExecutorJobException(
                    ErrorType.UNKNOWN,
                    "executor.job.empty-result",
                    "Executor вернул пустой результат job",
                    null,
                    Map.of(),
                    ExecutionStatus.FAILED);
        }
        return result;
    }

    private void publishLogs(JobMessage job, ExecutorJobResult result, Instant startedAt) {
        if (result.logs() == null || result.logs().isBlank()) {
            return;
        }

        String redactedLogs = secretRedactor.redact(result.logs());
        ExecutorEventMessage logEvent = event(
                job,
                EventType.JOB_LOG,
                ExecutionStatus.RUNNING,
                resultDuration(startedAt),
                List.of(),
                Map.of(
                        "lines",
                        countLines(redactedLogs),
                        "bytes",
                        redactedLogs != null ? redactedLogs.getBytes(StandardCharsets.UTF_8).length : 0),
                "Логи job опубликованы",
                null,
                redactedLogs,
                Map.of("logOnly", true));
        publishLog(logEvent);
    }

    private void publishArtifacts(JobMessage job, ExecutorJobResult result, Instant startedAt) {
        if (result.artifacts().isEmpty()) {
            return;
        }

        publishEvent(event(
                job,
                EventType.JOB_ARTIFACT,
                ExecutionStatus.RUNNING,
                resultDuration(startedAt),
                result.artifacts(),
                result.metrics(),
                "Артефакты job опубликованы",
                null,
                null,
                result.additionalData()));
    }

    private ExecutorEventMessage failedEvent(JobMessage job, Instant startedAt, ExecutorJobException exception) {
        return event(
                job,
                EventType.JOB_FINISHED,
                exception.status(),
                resultDuration(startedAt),
                List.of(),
                Map.of(),
                exception.getMessage(),
                new ExecutorError(
                        exception.errorType(),
                        exception.code(),
                        exception.getMessage(),
                        exception.details(),
                        exception.metadata()),
                null,
                Map.of());
    }

    @SuppressWarnings("java:S107")
    private ExecutorEventMessage event(
            JobMessage job,
            EventType eventType,
            ExecutionStatus status,
            Long durationMs,
            List<ArtifactDescriptor> artifacts,
            Map<String, Object> metrics,
            String summary,
            ExecutorError error,
            String logs,
            Map<String, Object> additionalData) {
        return new ExecutorEventMessage(
                job.schemaVersion(),
                messageIdSupplier.get(),
                job.correlationId(),
                job.pipelineRunId(),
                job.pipelineId(),
                job.stageId(),
                job.jobId(),
                job.jobExecutionId(),
                job.jobType(),
                job.templatePath(),
                eventType,
                status,
                job.attempt(),
                workerId,
                durationMs,
                artifacts,
                metrics,
                summary,
                error,
                logs,
                additionalData);
    }

    private Long resultDuration(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).toMillis();
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\R", -1).length;
    }

    private boolean shouldPreserveAsFailure(ExecutionStatus status) {
        return status != ExecutionStatus.SUCCESS && status != ExecutionStatus.SKIPPED;
    }

    private void publishEvent(ExecutorEventMessage event) {
        await(eventPublisher.publish(event));
    }

    private void publishLog(ExecutorEventMessage event) {
        await(logPublisher.publish(event));
    }

    /**
     * Синхронно дожидается завершения async publisher-а, чтобы pipeline не переходил к следующему шагу
     * до фактической публикации события или log-документа. Ошибка Kafka/OpenSearch должна прервать job
     * handler через {@link java.util.concurrent.CompletionException}, а не потеряться как фоновая ошибка.
     */
    private void await(CompletionStage<Void> stage) {
        stage.toCompletableFuture().join();
    }
}
