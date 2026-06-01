package ru.diplom.cicd.master.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.CreateExternalConnectionRequest;
import ru.diplom.cicd.master.domain.entity.ExternalConnectionEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.ExternalConnectionRepository;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.UserContextService;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@RestController
@RequestMapping("/api/v1/external-connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ExternalConnectionRepository externalConnectionRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @GetMapping
    public PageResponse<ExternalConnectionEntity> list(
            @RequestParam(name = "connectionType", required = false) String connectionType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.MANAGE_CONNECTIONS, "system", null);
        var items = externalConnectionRepository.findAll().stream()
                .filter(connection -> connectionType == null || connectionType.equalsIgnoreCase(connection.getConnectionType()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @PostMapping
    public Object create(@RequestBody CreateExternalConnectionRequest request) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.MANAGE_CONNECTIONS, "system", null);
        sensitiveDataSanitizer.requireNoInlineSecrets(request.config(), "config");
        ExternalConnectionEntity entity = ExternalConnectionEntity.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .connectionType(request.connectionType())
                .url(request.url())
                .credentialsRef(request.credentialsRef())
                .secretRefId(request.secretRefId())
                .config(request.config() == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(request.config()))
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        return externalConnectionRepository.save(entity);
    }
}

