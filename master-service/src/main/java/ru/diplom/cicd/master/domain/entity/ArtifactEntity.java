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
@Table(name = "artifact", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtifactEntity {
    @Id
    private UUID id;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String uri;

    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "retention_policy", nullable = false)
    private String retentionPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "checksum_verified", nullable = false)
    private Boolean checksumVerified;
}
