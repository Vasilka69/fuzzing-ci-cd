package ru.diplom.cicd.master.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.PermissionAssignmentEntity;
import ru.diplom.cicd.master.domain.entity.UserRoleAssignmentEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.PermissionAssignmentRepository;
import ru.diplom.cicd.master.repository.UserRoleAssignmentRepository;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, Permission permission, String resourceType, UUID resourceId) {
        if (userId == null) {
            return false;
        }

        List<PermissionAssignmentEntity> assignments = new ArrayList<>(permissionAssignmentRepository.findByUserId(userId));

        List<UserRoleAssignmentEntity> roleAssignments = userRoleAssignmentRepository.findByUserId(userId);
        Collection<UUID> roleIds = roleAssignments.stream().map(UserRoleAssignmentEntity::getRoleId).toList();
        if (!roleIds.isEmpty()) {
            assignments.addAll(permissionAssignmentRepository.findByRoleIdIn(roleIds));
        }

        boolean allow = false;
        for (PermissionAssignmentEntity assignment : assignments) {
            if (!assignment.getPermission().equals(permission.value())
                    && !assignment.getPermission().equals(Permission.ADMIN.value())) {
                continue;
            }
            if (!matchesResource(assignment, resourceType, resourceId)) {
                continue;
            }
            if ("deny".equalsIgnoreCase(assignment.getEffect())) {
                return false;
            }
            if ("allow".equalsIgnoreCase(assignment.getEffect())) {
                allow = true;
            }
        }
        return allow;
    }

    public void require(UUID userId, Permission permission, String resourceType, UUID resourceId) {
        if (!hasPermission(userId, permission, resourceType, resourceId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "access_denied",
                    "Permission denied: " + permission.value() + " on " + resourceType);
        }
    }

    private boolean matchesResource(PermissionAssignmentEntity assignment, String resourceType, UUID resourceId) {
        if ("system".equals(assignment.getResourceType())) {
            return true;
        }
        if (!assignment.getResourceType().equals(resourceType)) {
            return false;
        }
        if (assignment.getResourceId() == null && resourceId == null) {
            return true;
        }
        return assignment.getResourceId() != null && assignment.getResourceId().equals(resourceId);
    }
}
