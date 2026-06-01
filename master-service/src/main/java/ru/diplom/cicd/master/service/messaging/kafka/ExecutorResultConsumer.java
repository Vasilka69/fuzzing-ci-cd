package ru.diplom.cicd.master.service.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.service.messaging.ExecutorEventService;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutorResultConsumer {

    private final ExecutorEventService executorEventService;
    private final AppProperties appProperties;

    @KafkaListener(topics = "${app.messaging.results-topic:jobs.results}", groupId = "${spring.kafka.consumer.group-id:master-service}")
    public void consume(
            ExecutorEventMessage message,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        if (!"kafka".equalsIgnoreCase(appProperties.getMessaging().getExecutorEventsTransport())) {
            return;
        }
        try {
            executorEventService.handle("kafka-results-consumer", topic, key, "kafka", null, message);
        } catch (Exception ex) {
            log.error("Failed to process executor event {}", message, ex);
            throw ex;
        }
    }
}
