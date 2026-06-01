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
@Table(name = "cancellation_request", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationRequestEntity {
    @Id
    private UUID id;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "job_execution_id")
    private UUID jobExecutionId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "grace_period_seconds", nullable = false)
    private Integer gracePeriodSeconds;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;
}
