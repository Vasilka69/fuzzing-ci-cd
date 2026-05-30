package ru.diplom.cicd.executor.core.event;

import java.util.concurrent.CompletionStage;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;

/**
 * Публикует служебные события executor-а без больших текстовых логов.
 */
public interface ExecutorEventPublisher {

    CompletionStage<Void> publish(ExecutorEventMessage event);
}
