package ru.diplom.cicd.master.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.InboxEventEntity;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByMessageIdAndConsumerName(UUID messageId, String consumerName);
    boolean existsByEventSourceAndSourceDocumentIdAndConsumerName(String eventSource, String sourceDocumentId, String consumerName);
}
