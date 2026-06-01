package ru.diplom.cicd.master.service.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;
import ru.diplom.cicd.master.service.messaging.contract.JobMessage;

@Component
@RequiredArgsConstructor
public class JobMessageFactory {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    public JobMessage build(PipelineRunEntity run, JobEntity job, JobExecutionEntity execution, UUID pipelineId, UUID stageId, String templatePath) {
        Map<String, Object> params = toMap(job.getParams());
        Map<String, Object> resourceLimits = toMap(job.getResourceLimits());
        Map<String, Object> workspacePolicy = toMap(job.getSandboxPolicy());

        return new JobMessage(
                1,
                UUID.randomUUID(),
                run.getCorrelationId(),
                run.getId(),
                pipelineId,
                stageId,
                job.getId(),
                execution.getId(),
                job.getJobType(),
                templatePath == null ? job.getJobType() + "/unknown-template" : templatePath,
                execution.getAttempt(),
                job.getMaxAttempts(),
                job.getTimeoutSeconds(),
                resourceLimits,
                workspacePolicy,
                new HashMap<>(),
                params,
                Map.of("refs", new Object[0]),
                OffsetDateTime.now()
        );
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new HashMap<>();
        }
        return objectMapper.convertValue(sensitiveDataSanitizer.redact(value), MAP_TYPE);
    }
}
