package ru.diplom.cicd.demo.mockmaster.publisher;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.demo.mockmaster.pipeline.DemoJobPublication;

@Component
public final class KafkaDemoJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoJobPublisher.class);

    private final KafkaTemplate<String, JobMessage> kafkaTemplate;

    public KafkaDemoJobPublisher(KafkaTemplate<String, JobMessage> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
    }

    public void publish(List<DemoJobPublication> publications, Duration sendTimeout) {
        List<DemoJobPublication> safePublications = List.copyOf(publications);
        Duration effectiveTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
        log.info("Публикуется demo pipeline: jobs={}", safePublications.size());
        for (DemoJobPublication publication : safePublications) {
            publish(publication, effectiveTimeout);
        }
        log.info("Demo pipeline опубликован: jobs={}", safePublications.size());
    }

    private void publish(DemoJobPublication publication, Duration sendTimeout) {
        try {
            kafkaTemplate
                .send(publication.topic(), publication.key(), publication.message())
                .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (log.isInfoEnabled()) {
                log.info(
                    "Demo job опубликован: topic={}, key={}, templatePath={}",
                    publication.topic(),
                    publication.key(),
                    publication.message().templatePath());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Публикация demo job была прервана", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Не удалось опубликовать demo job в Kafka topic "
                            + publication.topic()
                            + " с key="
                            + publication.key(),
                    exception);
        }
    }
}
