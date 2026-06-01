package ru.diplom.cicd.master.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.SecretRefEntity;

public interface SecretRefRepository extends JpaRepository<SecretRefEntity, UUID> {
}
