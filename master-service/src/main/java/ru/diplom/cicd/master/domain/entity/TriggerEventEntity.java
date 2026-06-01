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
@Table(name = "trigger_event", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerEventEntity {
    @Id
    private UUID id;

    @Column(name = "trigger_id")
    private UUID triggerId;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "event_source", nullable = false)
    private String eventSource;

    @Column(name = "external_event_id")
    private String externalEventId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String ref;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "payload_hash")
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
