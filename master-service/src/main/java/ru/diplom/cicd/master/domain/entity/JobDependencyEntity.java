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
@Table(name = "job_dependency", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDependencyEntity {
    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "depends_on_job_id", nullable = false)
    private UUID dependsOnJobId;

    @Column(nullable = false)
    private String condition;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
