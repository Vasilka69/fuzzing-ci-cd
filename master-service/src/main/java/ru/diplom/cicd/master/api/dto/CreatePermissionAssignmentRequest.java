package ru.diplom.cicd.master.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreatePermissionAssignmentRequest(
        @NotBlank String subjectType,
        UUID userId,
        UUID roleId,
        @NotBlank String resourceType,
        UUID resourceId,
        @NotBlank String permission,
        String effect
) {
}
