package ru.diplom.cicd.master.api.controller;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.domain.entity.AuditEventEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.AuditEventRepository;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.UserContextService;

@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository auditEventRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;

    @GetMapping
    public PageResponse<AuditEventEntity> list(
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @RequestParam(name = "actorId", required = false) UUID actorId,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.ADMIN, "system", null);
        var items = auditEventRepository.findAll().stream()
                .filter(event -> from == null || !event.getCreatedAt().isBefore(from))
                .filter(event -> to == null || !event.getCreatedAt().isAfter(to))
                .filter(event -> actorId == null || actorId.equals(event.getActorUserId()))
                .filter(event -> resourceType == null || resourceType.equalsIgnoreCase(event.getEntityType()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }
}

