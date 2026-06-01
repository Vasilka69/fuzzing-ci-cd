package ru.diplom.cicd.master.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inbox_event", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxEventEntity {
    @Id
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "event_source", nullable = false)
    private String eventSource;

    @Column(name = "source_document_id")
    private String sourceDocumentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;
}
