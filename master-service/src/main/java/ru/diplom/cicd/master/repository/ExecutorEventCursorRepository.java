package ru.diplom.cicd.master.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.ExecutorEventCursorEntity;

public interface ExecutorEventCursorRepository extends JpaRepository<ExecutorEventCursorEntity, UUID> {
    Optional<ExecutorEventCursorEntity> findByConsumerName(String consumerName);
}
