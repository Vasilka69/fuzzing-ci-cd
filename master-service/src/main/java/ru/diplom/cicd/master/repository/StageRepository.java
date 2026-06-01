package ru.diplom.cicd.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.StageEntity;

public interface StageRepository extends JpaRepository<StageEntity, UUID> {
    List<StageEntity> findByPipelineIdOrderByPositionAsc(UUID pipelineId);
    List<StageEntity> findByPipelineIdIn(Collection<UUID> pipelineIds);
}
