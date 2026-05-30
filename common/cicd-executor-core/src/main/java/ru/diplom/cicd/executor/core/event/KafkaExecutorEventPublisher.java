package ru.diplom.cicd.executor.core.event;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;

/**
 * Kafka publisher для executor events. Kafka key намеренно равен {@code jobExecutionId},
 * чтобы все события одной попытки job сохраняли порядок внутри partition.
 */
public final class KafkaExecutorEventPublisher implements ExecutorEventPublisher {

    private final KafkaEventSender eventSender;
    private final String topic;

    public KafkaExecutorEventPublisher(KafkaTemplate<String, ExecutorEventMessage> kafkaTemplate) {
        this(kafkaTemplate, new KafkaExecutorEventPublisherProperties());
    }

    public KafkaExecutorEventPublisher(
            KafkaTemplate<String, ExecutorEventMessage> kafkaTemplate,
            KafkaExecutorEventPublisherProperties properties) {
        this(Objects.requireNonNull(kafkaTemplate, "kafkaTemplate")::send, properties);
    }

    KafkaExecutorEventPublisher(KafkaEventSender eventSender, KafkaExecutorEventPublisherProperties properties) {
        this.eventSender = Objects.requireNonNull(eventSender, "eventSender");
        this.topic = Objects.requireNonNull(properties, "properties").topic();
    }

    @Override
    public CompletionStage<Void> publish(ExecutorEventMessage event) {
        Objects.requireNonNull(event, "event");
        UUID jobExecutionId = Objects.requireNonNull(event.jobExecutionId(), "event.jobExecutionId");

        return eventSender.send(topic, jobExecutionId.toString(), event).thenApply(sendResult -> null);
    }

    @FunctionalInterface
    interface KafkaEventSender {
        CompletableFuture<SendResult<String, ExecutorEventMessage>> send(
                String topic, String key, ExecutorEventMessage event);
    }
}
