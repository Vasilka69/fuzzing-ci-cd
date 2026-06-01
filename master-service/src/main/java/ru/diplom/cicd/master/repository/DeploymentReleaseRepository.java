package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.DeploymentReleaseEntity;

public interface DeploymentReleaseRepository extends JpaRepository<DeploymentReleaseEntity, UUID> {
    List<DeploymentReleaseEntity> findByEnvironmentId(UUID environmentId);
    List<DeploymentReleaseEntity> findByStatus(String status);
}
