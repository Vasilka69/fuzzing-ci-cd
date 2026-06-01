package ru.diplom.cicd.master.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.pipeline.CreatePipelineRequest;
import ru.diplom.cicd.master.api.dto.pipeline.PipelineDetailsResponse;
import ru.diplom.cicd.master.api.dto.pipeline.PipelineResponse;
import ru.diplom.cicd.master.api.dto.pipeline.UpdatePipelineRequest;
import ru.diplom.cicd.master.api.mapper.PipelineMapper;
import ru.diplom.cicd.master.service.PipelineService;
import ru.diplom.cicd.master.service.PipelineValidationService;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineMapper pipelineMapper;
    private final PipelineValidationService pipelineValidationService;

    @GetMapping
    public PageResponse<PipelineResponse> list(
            @RequestParam(name = "folderId", required = false) UUID folderId,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<PipelineResponse> items = pipelineService.list(folderId, isActive, query).stream()
                .map(pipelineMapper::toPipeline)
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @PostMapping
    public PipelineResponse create(@RequestBody @Valid CreatePipelineRequest request) {
        return pipelineMapper.toPipeline(pipelineService.create(request));
    }

    @GetMapping("/{id}")
    public PipelineDetailsResponse get(@PathVariable("id") UUID id) {
        return pipelineService.getDetails(id);
    }

    @PutMapping("/{id}")
    public PipelineResponse update(@PathVariable("id") UUID id, @RequestBody UpdatePipelineRequest request) {
        return pipelineMapper.toPipeline(pipelineService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") UUID id) {
        pipelineService.deactivate(id);
    }

    @PostMapping("/{id}/validate")
    public Map<String, Object> validate(@PathVariable("id") UUID id) {
        var result = pipelineValidationService.validate(id);
        return Map.of("valid", result.valid(), "errors", result.errors());
    }
}

