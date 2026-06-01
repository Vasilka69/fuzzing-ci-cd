package ru.diplom.cicd.master.api.controller;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.CreatePermissionAssignmentRequest;
import ru.diplom.cicd.master.domain.entity.PermissionAssignmentEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.PermissionAssignmentRepository;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.UserContextService;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;

    @GetMapping
    public PageResponse<PermissionAssignmentEntity> list(
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "resourceId", required = false) UUID resourceId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.ADMIN, "system", null);
        var items = permissionAssignmentRepository.findAll().stream()
                .filter(a -> resourceType == null || resourceType.equalsIgnoreCase(a.getResourceType()))
                .filter(a -> resourceId == null || resourceId.equals(a.getResourceId()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @PostMapping
    public Object create(@RequestBody CreatePermissionAssignmentRequest request) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.ADMIN, "system", null);
        PermissionAssignmentEntity entity = PermissionAssignmentEntity.builder()
                .id(UUID.randomUUID())
                .subjectType(request.subjectType())
                .userId(request.userId())
                .roleId(request.roleId())
                .resourceType(request.resourceType())
                .resourceId(request.resourceId())
                .permission(request.permission())
                .effect(request.effect() == null ? "allow" : request.effect())
                .createdAt(OffsetDateTime.now())
                .build();
        return permissionAssignmentRepository.save(entity);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") UUID id) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.ADMIN, "system", null);
        permissionAssignmentRepository.deleteById(id);
    }
}

