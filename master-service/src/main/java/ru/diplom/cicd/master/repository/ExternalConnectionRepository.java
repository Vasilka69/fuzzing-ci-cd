package ru.diplom.cicd.master.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.ExternalConnectionEntity;

public interface ExternalConnectionRepository extends JpaRepository<ExternalConnectionEntity, UUID> {
}
