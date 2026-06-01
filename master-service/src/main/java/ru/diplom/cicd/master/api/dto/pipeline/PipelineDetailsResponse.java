package ru.diplom.cicd.master.api.dto.pipeline;

import java.util.List;

public record PipelineDetailsResponse(
        PipelineResponse pipeline,
        List<StageResponse> stages,
        List<JobResponse> jobs,
        List<DependencyResponse> dependencies
) {
}
