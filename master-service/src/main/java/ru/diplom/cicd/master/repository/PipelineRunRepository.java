package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;

public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, UUID> {
    List<PipelineRunEntity> findByPipelineIdOrderByStartedAtDesc(UUID pipelineId);
    List<PipelineRunEntity> findByStatusOrderByStartedAtDesc(String status, Pageable pageable);
    long countByPipelineIdAndTriggerIdAndStatusIn(UUID pipelineId, UUID triggerId, List<String> statuses);
}
