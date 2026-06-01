package ru.diplom.cicd.master.api.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.dto.pipeline.CreateDependencyRequest;
import ru.diplom.cicd.master.api.dto.pipeline.CreateJobRequest;
import ru.diplom.cicd.master.api.dto.pipeline.CreateStageRequest;
import ru.diplom.cicd.master.api.dto.pipeline.DependencyResponse;
import ru.diplom.cicd.master.api.dto.pipeline.JobResponse;
import ru.diplom.cicd.master.api.dto.pipeline.StageResponse;
import ru.diplom.cicd.master.api.mapper.PipelineMapper;
import ru.diplom.cicd.master.service.PipelineService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PipelineStructureController {

    private final PipelineService pipelineService;
    private final PipelineMapper pipelineMapper;

    @PostMapping("/pipelines/{id}/stages")
    public StageResponse createStage(@PathVariable("id") UUID id, @RequestBody @Valid CreateStageRequest request) {
        return pipelineMapper.toStage(pipelineService.createStage(id, request));
    }

    @PostMapping("/stages/{id}/jobs")
    public JobResponse createJob(@PathVariable("id") UUID id, @RequestBody @Valid CreateJobRequest request) {
        return pipelineMapper.toJob(pipelineService.createJob(id, request));
    }

    @PostMapping("/jobs/{id}/dependencies")
    public DependencyResponse addDependency(@PathVariable("id") UUID id, @RequestBody @Valid CreateDependencyRequest request) {
        return pipelineMapper.toDependency(pipelineService.addDependency(id, request));
    }
}

