package ru.diplom.cicd.master.service.messaging.inbox;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.InboxEventEntity;
import ru.diplom.cicd.master.repository.InboxEventRepository;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@Service
@RequiredArgsConstructor
public class InboxDeduplicationService {

    private final InboxEventRepository inboxEventRepository;

    @Transactional(readOnly = true)
    public boolean isDuplicate(String consumerName, String eventSource, String sourceDocumentId, UUID messageId) {
        if (messageId != null && inboxEventRepository.existsByMessageIdAndConsumerName(messageId, consumerName)) {
            return true;
        }
        return sourceDocumentId != null
                && inboxEventRepository.existsByEventSourceAndSourceDocumentIdAndConsumerName(eventSource, sourceDocumentId, consumerName);
    }

    @Transactional
    public void registerProcessed(String consumerName, String topic, String messageKey, String eventSource, String sourceDocumentId, ExecutorEventMessage message) {
        InboxEventEntity entity = InboxEventEntity.builder()
                .id(UUID.randomUUID())
                .messageId(message.messageId())
                .consumerName(consumerName)
                .topic(topic)
                .messageKey(messageKey)
                .jobExecutionId(message.jobExecutionId())
                .eventSource(eventSource)
                .sourceDocumentId(sourceDocumentId)
                .eventType(message.eventType())
                .schemaVersion(message.schemaVersion())
                .processedAt(OffsetDateTime.now())
                .status("processed")
                .build();
        inboxEventRepository.save(entity);
    }
}
