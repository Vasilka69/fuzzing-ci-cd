package ru.diplom.cicd.master.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "executor_event_cursor", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutorEventCursorEntity {
    @Id
    private UUID id;

    @Column(name = "consumer_name", nullable = false, unique = true)
    private String consumerName;

    @Column(name = "event_source", nullable = false)
    private String eventSource;

    @Column(name = "index_name")
    private String indexName;

    @Column(name = "last_ingested_at")
    private OffsetDateTime lastIngestedAt;

    @Column(name = "last_document_id")
    private String lastDocumentId;

    @Column(name = "last_message_id")
    private UUID lastMessageId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;
}
