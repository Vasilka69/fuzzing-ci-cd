package ru.diplom.cicd.master.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.CancellationRequestEntity;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequestEntity, UUID> {
}
