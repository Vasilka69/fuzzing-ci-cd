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
@Table(name = "storage_object", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageObjectEntity {
    @Id
    private UUID id;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "storage_uri", nullable = false, unique = true)
    private String storageUri;

    @Column(nullable = false)
    private String backend;

    private String bucket;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "upload_status", nullable = false)
    private String uploadStatus;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private String sha256;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "retention_policy", nullable = false)
    private String retentionPolicy;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
