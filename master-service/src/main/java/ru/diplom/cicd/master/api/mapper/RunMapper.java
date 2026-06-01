package ru.diplom.cicd.master.api.mapper;

import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.api.dto.run.JobExecutionResponse;
import ru.diplom.cicd.master.api.dto.run.PipelineRunResponse;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;

@Component
public class RunMapper {

    public PipelineRunResponse toRun(PipelineRunEntity entity) {
        return new PipelineRunResponse(
                entity.getId(),
                entity.getPipelineId(),
                entity.getStatus(),
                entity.getCorrelationId(),
                entity.getStartedBy(),
                entity.getTriggeredByType(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getSummary()
        );
    }

    public JobExecutionResponse toExecution(JobExecutionEntity entity) {
        return new JobExecutionResponse(
                entity.getId(),
                entity.getPipelineRunId(),
                entity.getJobId(),
                entity.getAttempt(),
                entity.getStatus(),
                entity.getWorkerId(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getDurationMs(),
                entity.getErrorType(),
                entity.getErrorCode(),
                entity.getErrorMessage()
        );
    }
}
