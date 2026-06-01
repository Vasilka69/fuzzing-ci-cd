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
@Table(name = "job_template", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTemplateEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String path;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_template", nullable = false, columnDefinition = "jsonb")
    private JsonNode paramsTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_params", nullable = false, columnDefinition = "jsonb")
    private JsonNode defaultParams;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
