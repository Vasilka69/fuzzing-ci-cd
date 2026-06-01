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
@Table(name = "pipeline_run", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRunEntity {
    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "trigger_id")
    private UUID triggerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "triggered_by_type", nullable = false)
    private String triggeredByType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode triggerPayload;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    private String summary;
}
