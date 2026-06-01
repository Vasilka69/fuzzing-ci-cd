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
@Table(name = "job", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEntity {
    @Id
    private UUID id;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(name = "job_template_id")
    private UUID jobTemplateId;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String name;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode params;

    @Column(columnDefinition = "text")
    private String script;

    @Column(name = "is_script_primary", nullable = false)
    private Boolean isScriptPrimary;

    @Column(nullable = false)
    private String condition;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_limits", nullable = false, columnDefinition = "jsonb")
    private JsonNode resourceLimits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sandbox_policy", nullable = false, columnDefinition = "jsonb")
    private JsonNode sandboxPolicy;

    @Column(name = "continue_on_error", nullable = false)
    private Boolean continueOnError;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
