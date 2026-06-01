package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.DeploymentApprovalEntity;

public interface DeploymentApprovalRepository extends JpaRepository<DeploymentApprovalEntity, UUID> {
    List<DeploymentApprovalEntity> findByPipelineRunId(UUID pipelineRunId);
    List<DeploymentApprovalEntity> findByStatus(String status);
}
