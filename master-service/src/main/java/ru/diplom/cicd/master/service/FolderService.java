package ru.diplom.cicd.master.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.api.dto.pipeline.CreateFolderRequest;
import ru.diplom.cicd.master.domain.entity.FolderEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.FolderRepository;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FolderEntity> list(UUID parentId) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        return folderRepository.findAll().stream()
                .filter(folder -> parentId == null ? folder.getParentId() == null : parentId.equals(folder.getParentId()))
                .toList();
    }

    @Transactional
    public FolderEntity create(CreateFolderRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "system", null);
        FolderEntity entity = FolderEntity.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .description(request.description())
                .parentId(request.parentId())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        FolderEntity saved = folderRepository.save(entity);
        auditService.record(userId, "FOLDER_CREATE", "folder", saved.getId(), saved);
        return saved;
    }
}
