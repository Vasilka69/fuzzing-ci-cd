package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.JobTemplateEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.JobTemplateRepository;

@Service
@RequiredArgsConstructor
public class JobTemplateService {

    private final JobTemplateRepository jobTemplateRepository;
    private final PermissionService permissionService;
    private final UserContextService userContextService;

    @Transactional(readOnly = true)
    public List<JobTemplateEntity> list(String jobType) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        if (jobType == null || jobType.isBlank()) {
            return jobTemplateRepository.findAll().stream()
                    .filter(template -> Boolean.TRUE.equals(template.getIsActive()))
                    .toList();
        }
        return jobTemplateRepository.findByJobTypeAndIsActive(jobType, true);
    }

    @Transactional(readOnly = true)
    public JobTemplateEntity get(UUID id) {
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
        return jobTemplateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "template_not_found", "Template not found"));
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID id, JsonNode params) {
        JobTemplateEntity template = get(id);
        if (params == null || !params.isObject()) {
            return new ValidationResult(false, List.of("params must be a JSON object"));
        }
        JsonNode templateNode = template.getParamsTemplate();
        if (templateNode != null && templateNode.isObject()) {
            List<String> missing = new java.util.ArrayList<>();
            templateNode.fieldNames().forEachRemaining(field -> {
                if (!params.has(field)) {
                    missing.add(field);
                }
            });
            if (!missing.isEmpty()) {
                return new ValidationResult(false, missing.stream().map(f -> "missing field: " + f).toList());
            }
        }
        return new ValidationResult(true, List.of());
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}
