package ru.diplom.cicd.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.JobDependencyEntity;

public interface JobDependencyRepository extends JpaRepository<JobDependencyEntity, UUID> {
    List<JobDependencyEntity> findByJobIdIn(Collection<UUID> jobIds);
    List<JobDependencyEntity> findByDependsOnJobId(UUID dependsOnJobId);
}
