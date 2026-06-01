package ru.diplom.cicd.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.PermissionAssignmentEntity;

public interface PermissionAssignmentRepository extends JpaRepository<PermissionAssignmentEntity, UUID> {
    List<PermissionAssignmentEntity> findByUserId(UUID userId);
    List<PermissionAssignmentEntity> findByRoleIdIn(Collection<UUID> roleIds);
}
