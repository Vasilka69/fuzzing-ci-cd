package ru.diplom.cicd.master.service.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.OutboxEventEntity;
import ru.diplom.cicd.master.repository.OutboxEventRepository;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEventEntity enqueue(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String topic,
            String messageKey,
            Object payload
    ) {
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .schemaVersion(1)
                .topic(topic)
                .messageKey(messageKey)
                .payload(objectMapper.valueToTree(payload))
                .headers(objectMapper.createObjectNode())
                .status("pending")
                .attempts(0)
                .createdAt(OffsetDateTime.now())
                .build();
        return outboxEventRepository.save(entity);
    }
}
