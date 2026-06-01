package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.ArtifactEntity;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, UUID> {
    List<ArtifactEntity> findByJobExecutionId(UUID jobExecutionId);
    List<ArtifactEntity> findByPipelineRunId(UUID pipelineRunId);
}
