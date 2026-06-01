package ru.diplom.cicd.master.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.TriggerEventEntity;

public interface TriggerEventRepository extends JpaRepository<TriggerEventEntity, UUID> {
    boolean existsByEventSourceAndExternalEventId(String eventSource, String externalEventId);
    boolean existsByEventSourceAndIdempotencyKey(String eventSource, String idempotencyKey);
    boolean existsByEventSourceAndPayloadHash(String eventSource, String payloadHash);
    Optional<TriggerEventEntity> findByEventSourceAndExternalEventId(String eventSource, String externalEventId);
    Optional<TriggerEventEntity> findByEventSourceAndIdempotencyKey(String eventSource, String idempotencyKey);
    Optional<TriggerEventEntity> findByEventSourceAndPayloadHash(String eventSource, String payloadHash);
}
