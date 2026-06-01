package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.AuditEventEntity;
import ru.diplom.cicd.master.repository.AuditEventRepository;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @Transactional
    public void record(UUID actorId, String eventType, String entityType, UUID entityId, Object payload) {
        var sanitized = payload == null ? objectMapper.createObjectNode() : sensitiveDataSanitizer.redact(payload);
        ObjectNode node;
        if (sanitized instanceof ObjectNode objectNode) {
            node = objectNode;
        } else {
            node = objectMapper.createObjectNode();
            node.set("value", sanitized);
        }
        AuditEventEntity event = AuditEventEntity.builder()
                .id(UUID.randomUUID())
                .actorUserId(actorId)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .payload(node)
                .createdAt(OffsetDateTime.now())
                .build();
        auditEventRepository.save(event);
    }
}
