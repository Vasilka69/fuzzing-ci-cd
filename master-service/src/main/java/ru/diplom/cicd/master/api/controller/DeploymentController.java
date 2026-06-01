package ru.diplom.cicd.master.api.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.domain.entity.DeploymentApprovalEntity;
import ru.diplom.cicd.master.domain.entity.DeploymentEnvironmentEntity;
import ru.diplom.cicd.master.domain.entity.DeploymentReleaseEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.repository.DeploymentApprovalRepository;
import ru.diplom.cicd.master.repository.DeploymentEnvironmentRepository;
import ru.diplom.cicd.master.repository.DeploymentReleaseRepository;
import ru.diplom.cicd.master.service.DeploymentApprovalService;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.UserContextService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentEnvironmentRepository deploymentEnvironmentRepository;
    private final DeploymentApprovalRepository deploymentApprovalRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentApprovalService deploymentApprovalService;
    private final PermissionService permissionService;
    private final UserContextService userContextService;

    @GetMapping("/environments")
    public PageResponse<DeploymentEnvironmentEntity> environments(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        return PaginationHelper.paginate(deploymentEnvironmentRepository.findAll(), page, size);
    }

    @PostMapping("/deployment-approvals/{id}/approve")
    public Object approve(@PathVariable("id") UUID id) {
        return deploymentApprovalService.approve(id);
    }

    @PostMapping("/deployment-approvals/{id}/reject")
    public Object reject(@PathVariable("id") UUID id) {
        return deploymentApprovalService.reject(id);
    }

    @GetMapping("/deployment-releases")
    public PageResponse<DeploymentReleaseEntity> releases(
            @RequestParam(name = "environmentId", required = false) UUID environmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "releaseId", required = false) String releaseId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        var items = deploymentReleaseRepository.findAll().stream()
                .filter(release -> environmentId == null || environmentId.equals(release.getEnvironmentId()))
                .filter(release -> status == null || status.equalsIgnoreCase(release.getStatus()))
                .filter(release -> releaseId == null || releaseId.equalsIgnoreCase(release.getReleaseId()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @GetMapping("/deployment-approvals")
    public PageResponse<DeploymentApprovalEntity> approvals(
            @RequestParam(name = "pipelineRunId", required = false) UUID pipelineRunId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        var items = deploymentApprovalRepository.findAll().stream()
                .filter(approval -> pipelineRunId == null || pipelineRunId.equals(approval.getPipelineRunId()))
                .filter(approval -> status == null || status.equalsIgnoreCase(approval.getStatus()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }
}

