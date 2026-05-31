package ru.diplom.cicd.demo.mockmaster.publisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.demo.mockmaster.pipeline.DemoJobPublication;

class KafkaDemoJobPublisherTest {

    private final KafkaTemplate<String, JobMessage> kafkaTemplate = mock();
    private final KafkaDemoJobPublisher publisher = new KafkaDemoJobPublisher(kafkaTemplate);

    @Test
    void sendsEachJobWithJobExecutionIdKey() {
        JobMessage message = message();
        when(kafkaTemplate.send("jobs.build", message.jobExecutionId().toString(), message))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(List.of(new DemoJobPublication("jobs.build", message)), Duration.ofSeconds(1), Duration.ZERO);

        verify(kafkaTemplate).send("jobs.build", message.jobExecutionId().toString(), message);
    }

    @Test
    void wrapsKafkaSendFailureWithRussianMessage() {
        JobMessage message = message();
        CompletableFuture<SendResult<String, JobMessage>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send("jobs.build", message.jobExecutionId().toString(), message))
                .thenReturn(failedFuture);

        List<DemoJobPublication> publications = List.of(new DemoJobPublication("jobs.build", message));
        Duration sendTimeout = Duration.ofSeconds(1);
        assertThatThrownBy(() -> publisher.publish(publications, sendTimeout, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Не удалось опубликовать demo job в Kafka topic jobs.build");
    }

    private static JobMessage message() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new JobMessage(
                1,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                id,
                JobType.BUILD,
                "build/maven",
                1,
                1,
                60,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                null,
                Map.of(),
                Map.of("source_snapshot_uri", "storage://source-snapshots/demo/source-snapshot.tar.gz"),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T00:00:00Z"));
    }
}
