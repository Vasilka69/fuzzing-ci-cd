package ru.diplom.cicd.executor.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;

/**
 * Filesystem guard для single-node executor runtime-а.
 *
 * <p>Lock-file удерживается на время выполнения job и защищает от параллельной повторной доставки.
 * State-file остается вне очищаемого workspace, поэтому успешный результат переживает cleanup и позволяет
 * пропускать дальнейшие дубликаты того же {@code jobExecutionId}. Если JVM упала и оставила state
 * {@code RUNNING}, следующий процесс сможет взять lock и перезапустить job как stale attempt.
 */
public final class FileIdempotencyGuard implements IdempotencyGuard {

    private static final String STATE_EXTENSION = ".state.json";
    private static final String LOCK_EXTENSION = ".lock";

    private final Path root;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FileIdempotencyGuard(Path root) {
        this(root, new ObjectMapper(), Clock.systemUTC());
    }

    FileIdempotencyGuard(Path root, ObjectMapper objectMapper, Clock clock) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IdempotencyClaim acquire(JobMessage job) {
        Objects.requireNonNull(job, "job");
        UUID jobExecutionId = Objects.requireNonNull(job.jobExecutionId(), "job.jobExecutionId");

        try {
            Files.createDirectories(root);
            Path statePath = statePath(jobExecutionId);
            Path lockPath = lockPath(jobExecutionId);
            FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = tryLock(lockChannel);
            if (lock == null) {
                closeQuietly(lockChannel);
                return new SkippedClaim(IdempotencyDecision.duplicateRunning(readUpdatedAt(statePath)));
            }

            return acquireLocked(job, statePath, lock, lockChannel);
        } catch (IOException exception) {
            throw new IdempotencyException("Не удалось проверить idempotency marker job: " + jobExecutionId, exception);
        }
    }

    private IdempotencyClaim acquireLocked(JobMessage job, Path statePath, FileLock lock, FileChannel lockChannel)
            throws IOException {
        try {
            IdempotencyState currentState = readState(statePath);
            if (currentState != null && isDuplicateCompleted(currentState.status())) {
                release(lock, lockChannel);
                return new SkippedClaim(IdempotencyDecision.duplicateCompleted(
                        currentState.status(),
                        currentState.summary(),
                        parseInstant(currentState.updatedAt()),
                        currentState.metadata()));
            }

            writeState(
                    statePath,
                    IdempotencyState.running(
                            job.schemaVersion(),
                            job.messageId(),
                            job.attempt(),
                            clock.instant().toString()));
            return new FileClaim(statePath, lock, lockChannel);
        } catch (IOException | RuntimeException exception) {
            release(lock, lockChannel);
            throw exception;
        }
    }

    private FileLock tryLock(FileChannel lockChannel) throws IOException {
        try {
            return lockChannel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        }
    }

    private IdempotencyState readState(Path statePath) throws IOException {
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        return objectMapper.readValue(statePath.toFile(), IdempotencyState.class);
    }

    private Instant readUpdatedAt(Path statePath) throws IOException {
        IdempotencyState state = readState(statePath);
        return state == null ? null : parseInstant(state.updatedAt());
    }

    private void writeState(Path statePath, IdempotencyState state) throws IOException {
        Path tempPath = Files.createTempFile(root, statePath.getFileName().toString(), ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), state);
        try {
            Files.move(tempPath, statePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, statePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path statePath(UUID jobExecutionId) {
        return root.resolve(jobExecutionId + STATE_EXTENSION);
    }

    private Path lockPath(UUID jobExecutionId) {
        return root.resolve(jobExecutionId + LOCK_EXTENSION);
    }

    private boolean isDuplicateCompleted(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.SKIPPED;
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private void release(FileLock lock, FileChannel channel) {
        try {
            lock.release();
        } catch (IOException exception) {
            throw new IdempotencyException("Не удалось освободить idempotency lock", exception);
        } finally {
            closeQuietly(channel);
        }
    }

    private void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Закрытие lock channel не должно маскировать результат job.
        }
    }

    private record SkippedClaim(IdempotencyDecision decision) implements IdempotencyClaim {

        @Override
        public void complete(ExecutorEventMessage event) {
            Objects.requireNonNull(event, "event");
        }

        @Override
        public void close() {
            // Lock не удерживается для duplicate delivery.
        }
    }

    private final class FileClaim implements IdempotencyClaim {

        private final Path statePath;
        private final FileLock lock;
        private final FileChannel channel;
        private boolean closed;

        private FileClaim(Path statePath, FileLock lock, FileChannel channel) {
            this.statePath = statePath;
            this.lock = lock;
            this.channel = channel;
        }

        @Override
        public IdempotencyDecision decision() {
            return IdempotencyDecision.started();
        }

        @Override
        public void complete(ExecutorEventMessage event) {
            Objects.requireNonNull(event, "event");
            try {
                writeState(
                        statePath,
                        IdempotencyState.completed(event, clock.instant().toString()));
            } catch (IOException exception) {
                throw new IdempotencyException(
                        "Не удалось сохранить idempotency result job: " + event.jobExecutionId(), exception);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            release(lock, channel);
        }
    }

    private record IdempotencyState(
            int schemaVersion,
            UUID messageId,
            int attempt,
            ExecutionStatus status,
            String summary,
            String updatedAt,
            Map<String, Object> metadata) {

        private IdempotencyState {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        private static IdempotencyState running(int schemaVersion, UUID messageId, int attempt, String updatedAt) {
            return new IdempotencyState(
                    schemaVersion, messageId, attempt, ExecutionStatus.RUNNING, "Job выполняется", updatedAt, Map.of());
        }

        private static IdempotencyState completed(ExecutorEventMessage event, String updatedAt) {
            return new IdempotencyState(
                    event.schemaVersion(),
                    event.messageId(),
                    event.attempt(),
                    event.status(),
                    event.summary(),
                    updatedAt,
                    Map.of(
                            "eventType",
                            event.eventType().name(),
                            "jobType",
                            event.jobType().name(),
                            "templatePath",
                            event.templatePath()));
        }
    }
}
