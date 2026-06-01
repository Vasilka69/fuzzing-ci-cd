package ru.diplom.cicd.master.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.DeploymentApprovalEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.DeploymentApprovalRepository;

@Service
@RequiredArgsConstructor
public class DeploymentApprovalService {

    private final DeploymentApprovalRepository deploymentApprovalRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final PipelineRunService pipelineRunService;
    private final AuditService auditService;

    @Transactional
    public DeploymentApprovalEntity approve(UUID approvalId) {
        UUID userId = userContextService.currentUserIdOrNull();
        DeploymentApprovalEntity approval = deploymentApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "approval_not_found", "Approval not found"));
        permissionService.require(userId, Permission.APPROVE_DEPLOYMENT, "environment", approval.getEnvironmentId());
        if (!"pending".equalsIgnoreCase(approval.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "approval_not_pending", "Approval is already decided");
        }

        approval.setStatus("approved");
        approval.setApprovedBy(userId);
        approval.setDecidedAt(OffsetDateTime.now());
        DeploymentApprovalEntity saved = deploymentApprovalRepository.save(approval);
        pipelineRunService.enqueueApprovedExecution(saved.getId());
        auditService.record(userId, "DEPLOYMENT_APPROVAL_APPROVE", "deployment_approval", saved.getId(), null);
        return saved;
    }

    @Transactional
    public DeploymentApprovalEntity reject(UUID approvalId) {
        UUID userId = userContextService.currentUserIdOrNull();
        DeploymentApprovalEntity approval = deploymentApprovalRepository.findById(approvalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "approval_not_found", "Approval not found"));
        permissionService.require(userId, Permission.APPROVE_DEPLOYMENT, "environment", approval.getEnvironmentId());
        if (!"pending".equalsIgnoreCase(approval.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "approval_not_pending", "Approval is already decided");
        }

        approval.setStatus("rejected");
        approval.setApprovedBy(userId);
        approval.setDecidedAt(OffsetDateTime.now());
        DeploymentApprovalEntity saved = deploymentApprovalRepository.save(approval);
        pipelineRunService.rejectApprovalExecution(saved.getId());
        auditService.record(userId, "DEPLOYMENT_APPROVAL_REJECT", "deployment_approval", saved.getId(), null);
        return saved;
    }
}
