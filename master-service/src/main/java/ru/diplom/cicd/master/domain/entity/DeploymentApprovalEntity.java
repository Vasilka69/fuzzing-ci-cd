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
@Table(name = "deployment_approval", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentApprovalEntity {
    @Id
    private UUID id;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
