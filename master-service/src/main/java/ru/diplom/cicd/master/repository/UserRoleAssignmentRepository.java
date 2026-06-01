package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.UserRoleAssignmentEntity;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {
    List<UserRoleAssignmentEntity> findByUserId(UUID userId);
}
