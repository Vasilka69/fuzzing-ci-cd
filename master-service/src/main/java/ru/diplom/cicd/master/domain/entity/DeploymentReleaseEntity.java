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
@Table(name = "deployment_release", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentReleaseEntity {
    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false, unique = true)
    private String releaseId;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "target_connection_id")
    private UUID targetConnectionId;

    @Column(name = "artifact_uri", nullable = false)
    private String artifactUri;

    @Column(name = "artifact_sha256")
    private String artifactSha256;

    @Column(name = "deployment_type", nullable = false)
    private String deploymentType;

    @Column(nullable = false)
    private String status;

    @Column(name = "manifest_uri")
    private String manifestUri;

    @Column(name = "rollback_release_id")
    private String rollbackReleaseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "healthcheck_result", nullable = false, columnDefinition = "jsonb")
    private JsonNode healthcheckResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deployed_at")
    private OffsetDateTime deployedAt;
}
