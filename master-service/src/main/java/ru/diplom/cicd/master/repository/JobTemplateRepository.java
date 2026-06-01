package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.JobTemplateEntity;

public interface JobTemplateRepository extends JpaRepository<JobTemplateEntity, UUID> {
    List<JobTemplateEntity> findByJobTypeAndIsActive(String jobType, Boolean isActive);
    Optional<JobTemplateEntity> findByPath(String path);
}
