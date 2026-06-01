package ru.diplom.cicd.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.PipelineEntity;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {
    List<PipelineEntity> findByFolderIdAndIsActive(UUID folderId, Boolean isActive);
    List<PipelineEntity> findByIsActive(Boolean isActive);
    Optional<PipelineEntity> findByIdAndIsActive(UUID id, Boolean isActive);
}
