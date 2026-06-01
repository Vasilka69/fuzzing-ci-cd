package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.TriggerEntity;

public interface TriggerRepository extends JpaRepository<TriggerEntity, UUID> {
    List<TriggerEntity> findByPipelineId(UUID pipelineId);
    List<TriggerEntity> findByIsActiveTrue();
}
