package ru.diplom.cicd.executor.core.idempotency;

import ru.diplom.cicd.contracts.event.ExecutorEventMessage;

/**
 * Захват права выполнения job. Реализации могут удерживать filesystem lock до завершения обработки.
 */
public interface IdempotencyClaim extends AutoCloseable {

    IdempotencyDecision decision();

    default boolean shouldExecute() {
        return decision().shouldExecute();
    }

    void complete(ExecutorEventMessage event);

    @Override
    void close();
}
