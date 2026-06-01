package ru.diplom.cicd.master.service.messaging.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.entity.OutboxEventEntity;
import ru.diplom.cicd.master.repository.OutboxEventRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AppProperties appProperties;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT2S}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = outboxEventRepository.lockByStatus("pending");
        if (pending.isEmpty()) {
            return;
        }
        int batchSize = appProperties.getOutbox().getBatchSize();
        int max = Math.min(batchSize, pending.size());
        for (int i = 0; i < max; i++) {
            OutboxEventEntity event = pending.get(i);
            publishSingle(event);
        }
    }

    private void publishSingle(OutboxEventEntity event) {
        try {
            if (appProperties.getOutbox().isPublisherEnabled()) {
                Map<String, Object> payload = objectMapper.convertValue(event.getPayload(), MAP_TYPE);
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload);
            }
            event.setStatus("published");
            event.setPublishedAt(OffsetDateTime.now());
            event.setLastError(null);
        } catch (Exception ex) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(ex.getMessage());
            int maxAttempts = appProperties.getOutbox().getMaxAttempts();
            if (event.getAttempts() >= maxAttempts) {
                event.setStatus("failed");
            }
            log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage(), ex);
        }
        outboxEventRepository.save(event);
    }
}
