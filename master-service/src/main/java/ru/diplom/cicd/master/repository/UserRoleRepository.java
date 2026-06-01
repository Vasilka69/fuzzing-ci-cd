package ru.diplom.cicd.master.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.diplom.cicd.master.domain.entity.UserRoleEntity;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
    Optional<UserRoleEntity> findByCode(String code);
}
