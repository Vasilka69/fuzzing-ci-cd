package ru.diplom.cicd.master.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.api.dto.AuthLoginRequest;
import ru.diplom.cicd.master.api.dto.AuthLoginResponse;
import ru.diplom.cicd.master.api.dto.AuthMeResponse;
import ru.diplom.cicd.master.domain.entity.AppUserEntity;
import ru.diplom.cicd.master.domain.entity.UserRoleAssignmentEntity;
import ru.diplom.cicd.master.domain.entity.UserRoleEntity;
import ru.diplom.cicd.master.repository.AppUserRepository;
import ru.diplom.cicd.master.repository.UserRoleAssignmentRepository;
import ru.diplom.cicd.master.repository.UserRoleRepository;
import ru.diplom.cicd.master.security.RequestUser;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AuditService auditService;
    private final UserContextService userContextService;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        AppUserEntity user = appUserRepository.findByLogin(request.login())
                .orElseGet(() -> {
                    AppUserEntity created = AppUserEntity.builder()
                            .id(UUID.randomUUID())
                            .login(request.login())
                            .email(request.login() + "@local")
                            .passwordHash("{noop}" + request.password())
                            .displayName(request.login())
                            .isActive(true)
                            .createdAt(OffsetDateTime.now())
                            .updatedAt(OffsetDateTime.now())
                            .build();
                    return appUserRepository.save(created);
                });
        ensureDeveloperRole(user.getId());
        auditService.record(user.getId(), "AUTH_LOGIN", "app_user", user.getId(), null);
        return new AuthLoginResponse(user.getId(), user.getLogin(), "dev-token-" + user.getId(), "Bearer");
    }

    @Transactional(readOnly = true)
    public AuthMeResponse me() {
        RequestUser current = userContextService.current();
        if (current.id() == null) {
            return new AuthMeResponse(null, current.login(), List.of());
        }

        List<String> roles = userRoleAssignmentRepository.findByUserId(current.id()).stream()
                .map(UserRoleAssignmentEntity::getRoleId)
                .map(roleId -> userRoleRepository.findById(roleId).map(UserRoleEntity::getCode).orElse("UNKNOWN"))
                .toList();
        return new AuthMeResponse(current.id(), current.login(), roles);
    }

    private void ensureDeveloperRole(UUID userId) {
        boolean hasAnyRole = !userRoleAssignmentRepository.findByUserId(userId).isEmpty();
        if (hasAnyRole) {
            return;
        }
        userRoleRepository.findByCode("DEVELOPER").ifPresent(role -> {
            UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .roleId(role.getId())
                    .createdAt(OffsetDateTime.now())
                    .build();
            userRoleAssignmentRepository.save(assignment);
        });
    }
}
