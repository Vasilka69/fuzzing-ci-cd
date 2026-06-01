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
import ru.diplom.cicd.master.api.dto.CreateSecretRefRequest;
import ru.diplom.cicd.master.domain.entity.SecretRefEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.SecretRefRepository;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.UserContextService;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@RestController
@RequestMapping("/api/v1/secret-refs")
@RequiredArgsConstructor
public class SecretRefController {

    private final SecretRefRepository secretRefRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @GetMapping
    public PageResponse<SecretRefEntity> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.MANAGE_SECRETS, "system", null);
        return PaginationHelper.paginate(secretRefRepository.findAll(), page, size);
    }

    @PostMapping
    public Object create(@RequestBody CreateSecretRefRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.MANAGE_SECRETS, "system", null);
        sensitiveDataSanitizer.requireNoInlineSecrets(request.metadata(), "metadata");
        SecretRefEntity entity = SecretRefEntity.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .provider(request.provider())
                .externalKey(request.externalKey())
                .description(request.description())
                .scope(request.scope() == null ? "project" : request.scope())
                .metadata(request.metadata() == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(request.metadata()))
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        return secretRefRepository.save(entity);
    }
}

