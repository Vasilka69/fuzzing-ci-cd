package ru.diplom.cicd.master.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.FolderEntity;

public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
}
