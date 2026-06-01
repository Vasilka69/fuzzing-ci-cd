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
@Table(name = "permission_assignment", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionAssignmentEntity {
    @Id
    private UUID id;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(nullable = false)
    private String permission;

    @Column(nullable = false)
    private String effect;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
