package ru.diplom.cicd.executor.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobType;

class KafkaExecutorEventPublisherTest {

    @Test
    void publishSendsEventWithJobExecutionIdAsKafkaKey() {
        KafkaTemplate<String, ExecutorEventMessage> kafkaTemplate = kafkaTemplate();
        KafkaExecutorEventPublisher publisher = new KafkaExecutorEventPublisher(
                kafkaTemplate, new KafkaExecutorEventPublisherProperties("executor.events"));
        ExecutorEventMessage event = event();
        when(kafkaTemplate.send("executor.events", event.jobExecutionId().toString(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event).toCompletableFuture().join();

        verify(kafkaTemplate).send("executor.events", event.jobExecutionId().toString(), event);
    }

    @Test
    void defaultTopicMatchesLocalKafkaTopic() {
        KafkaExecutorEventPublisherProperties properties = new KafkaExecutorEventPublisherProperties("");

        assertEquals("jobs.results", properties.topic());
    }

    @Test
    void publishFailsFastWhenJobExecutionIdIsMissing() {
        KafkaExecutorEventPublisher publisher = new KafkaExecutorEventPublisher(
                new CapturingKafkaEventSender(), new KafkaExecutorEventPublisherProperties());
        ExecutorEventMessage eventWithoutJobExecutionId = new ExecutorEventMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                UUID.fromString("00000000-0000-0000-0000-000000000014"),
                UUID.fromString("00000000-0000-0000-0000-000000000015"),
                UUID.fromString("00000000-0000-0000-0000-000000000016"),
                null,
                JobType.BUILD,
                "build/maven",
                EventType.JOB_RUNNING,
                ExecutionStatus.RUNNING,
                1,
                "build-worker-1",
                null,
                List.of(),
                Map.of(),
                "Сборка запущена",
                null,
                null,
                Map.of());

        NullPointerException error =
                assertThrows(NullPointerException.class, () -> publisher.publish(eventWithoutJobExecutionId));

        assertEquals("event.jobExecutionId", error.getMessage());
    }

    @Test
    void publishUsesResolvedDefaultTopic() {
        CapturingKafkaEventSender sender = new CapturingKafkaEventSender();
        KafkaExecutorEventPublisher publisher =
                new KafkaExecutorEventPublisher(sender, new KafkaExecutorEventPublisherProperties());
        ExecutorEventMessage event = event();

        publisher.publish(event).toCompletableFuture().join();

        assertEquals("jobs.results", sender.topic);
        assertEquals(event.jobExecutionId().toString(), sender.key);
        assertSame(event, sender.event);
    }

    private ExecutorEventMessage event() {
        return new ExecutorEventMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                JobType.BUILD,
                "build/maven",
                EventType.JOB_RUNNING,
                ExecutionStatus.RUNNING,
                1,
                "build-worker-1",
                null,
                List.of(),
                Map.of(),
                "Сборка запущена",
                null,
                null,
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, ExecutorEventMessage> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    private static final class CapturingKafkaEventSender implements KafkaExecutorEventPublisher.KafkaEventSender {

        private String topic;
        private String key;
        private ExecutorEventMessage event;

        @Override
        public CompletableFuture<SendResult<String, ExecutorEventMessage>> send(
                String topic, String key, ExecutorEventMessage event) {
            this.topic = topic;
            this.key = key;
            this.event = event;
            return CompletableFuture.completedFuture(null);
        }
    }
}
