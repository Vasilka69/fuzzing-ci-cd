package ru.diplom.cicd.executor.core.log;

import java.util.concurrent.CompletionStage;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;

/**
 * Публикует текстовые executor-логи как отдельные {@code JOB_LOG} документы.
 * Текст логов должен быть очищен от секретов до вызова publisher-а.
 */
public interface ExecutorLogPublisher {

    CompletionStage<Void> publish(ExecutorEventMessage logEvent);
}
