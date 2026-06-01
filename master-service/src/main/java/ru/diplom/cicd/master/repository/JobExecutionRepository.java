package ru.diplom.cicd.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;

public interface JobExecutionRepository extends JpaRepository<JobExecutionEntity, UUID> {
    List<JobExecutionEntity> findByPipelineRunId(UUID pipelineRunId);
    List<JobExecutionEntity> findByPipelineRunIdAndStatusIn(UUID pipelineRunId, Collection<String> statuses);
    List<JobExecutionEntity> findByPipelineRunIdAndJobId(UUID pipelineRunId, UUID jobId);
    List<JobExecutionEntity> findByPipelineRunIdAndJobIdOrderByAttemptDesc(UUID pipelineRunId, UUID jobId);
    Optional<JobExecutionEntity> findFirstByPipelineRunIdAndJobIdOrderByAttemptDesc(UUID pipelineRunId, UUID jobId);
}
