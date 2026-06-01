package ru.diplom.cicd.master.api.mapper;

import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.api.dto.pipeline.DependencyResponse;
import ru.diplom.cicd.master.api.dto.pipeline.JobResponse;
import ru.diplom.cicd.master.api.dto.pipeline.PipelineResponse;
import ru.diplom.cicd.master.api.dto.pipeline.StageResponse;
import ru.diplom.cicd.master.domain.entity.JobDependencyEntity;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.PipelineEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;

@Component
public class PipelineMapper {

    public PipelineResponse toPipeline(PipelineEntity entity) {
        return new PipelineResponse(
                entity.getId(),
                entity.getFolderId(),
                entity.getName(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public StageResponse toStage(StageEntity entity) {
        return new StageResponse(
                entity.getId(),
                entity.getPipelineId(),
                entity.getPosition(),
                entity.getName(),
                entity.getDescription(),
                entity.getRunPolicy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public JobResponse toJob(JobEntity entity) {
        return new JobResponse(
                entity.getId(),
                entity.getStageId(),
                entity.getJobTemplateId(),
                entity.getPosition(),
                entity.getName(),
                entity.getJobType(),
                entity.getParams(),
                entity.getScript(),
                Boolean.TRUE.equals(entity.getIsScriptPrimary()),
                entity.getCondition(),
                entity.getTimeoutSeconds(),
                entity.getMaxAttempts(),
                entity.getResourceLimits(),
                entity.getSandboxPolicy(),
                Boolean.TRUE.equals(entity.getContinueOnError()),
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public DependencyResponse toDependency(JobDependencyEntity entity) {
        return new DependencyResponse(
                entity.getId(),
                entity.getJobId(),
                entity.getDependsOnJobId(),
                entity.getCondition(),
                entity.getCreatedAt()
        );
    }
}
