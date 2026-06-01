package ru.diplom.cicd.master.repository;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.StorageObjectEntity;

public interface StorageObjectRepository extends JpaRepository<StorageObjectEntity, UUID> {
    Optional<StorageObjectEntity> findByStorageUri(String storageUri);
}
