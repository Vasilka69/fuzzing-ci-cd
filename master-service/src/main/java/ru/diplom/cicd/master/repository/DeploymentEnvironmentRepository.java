package ru.diplom.cicd.master.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.DeploymentEnvironmentEntity;

public interface DeploymentEnvironmentRepository extends JpaRepository<DeploymentEnvironmentEntity, UUID> {
    Optional<DeploymentEnvironmentEntity> findByName(String name);
}
