package ru.diplom.cicd.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.JobEntity;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    List<JobEntity> findByStageIdOrderByPositionAsc(UUID stageId);
    List<JobEntity> findByStageIdInOrderByPositionAsc(Collection<UUID> stageIds);
}
