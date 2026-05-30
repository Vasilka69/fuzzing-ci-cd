package ru.diplom.cicd.executor.core.idempotency;

import java.util.Objects;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;

public interface IdempotencyGuard {

    IdempotencyClaim acquire(JobMessage job);

    static IdempotencyGuard noop() {
        return NoopIdempotencyGuard.INSTANCE;
    }

    @SuppressWarnings("java:S6548")
    enum NoopIdempotencyGuard implements IdempotencyGuard {
        INSTANCE;

        @Override
        public IdempotencyClaim acquire(JobMessage job) {
            Objects.requireNonNull(job, "job");
            return NoopIdempotencyClaim.INSTANCE;
        }
    }

    @SuppressWarnings("java:S6548")
    enum NoopIdempotencyClaim implements IdempotencyClaim {
        INSTANCE;

        @Override
        public IdempotencyDecision decision() {
            return IdempotencyDecision.started();
        }

        @Override
        public void complete(ExecutorEventMessage event) {
            Objects.requireNonNull(event, "event");
        }

        @Override
        public void close() {
            // No-op для legacy constructor-а без idempotency storage.
        }
    }
}
