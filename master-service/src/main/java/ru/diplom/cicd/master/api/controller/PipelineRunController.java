package ru.diplom.cicd.master.api.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.run.CreatePipelineRunRequest;
import ru.diplom.cicd.master.api.dto.run.PipelineRunResponse;
import ru.diplom.cicd.master.api.dto.run.RunEventsPageResponse;
import ru.diplom.cicd.master.api.mapper.RunMapper;
import ru.diplom.cicd.master.service.PipelineRunService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PipelineRunController {

    private final PipelineRunService pipelineRunService;
    private final RunMapper runMapper;

    @PostMapping("/pipelines/{id}/runs")
    public PipelineRunResponse runPipeline(@PathVariable("id") UUID id, @RequestBody(required = false) CreatePipelineRunRequest request) {
        return runMapper.toRun(pipelineRunService.createRun(id, request));
    }

    @GetMapping("/pipeline-runs")
    public PageResponse<PipelineRunResponse> listRuns(
            @RequestParam(name = "pipelineId", required = false) UUID pipelineId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "triggerType", required = false) String triggerType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<PipelineRunResponse> items = pipelineRunService.listRuns(pipelineId, status).stream()
                .map(runMapper::toRun)
                .filter(run -> triggerType == null || triggerType.equalsIgnoreCase(run.triggeredByType()))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @GetMapping("/pipeline-runs/{id}")
    public PipelineRunResponse getRun(@PathVariable("id") UUID id) {
        return runMapper.toRun(pipelineRunService.getRun(id));
    }

    @PostMapping("/pipeline-runs/{id}/cancel")
    public PipelineRunResponse cancelRun(@PathVariable("id") UUID id) {
        return runMapper.toRun(pipelineRunService.cancelRun(id));
    }

    @PostMapping("/pipeline-runs/{id}/retry")
    public PipelineRunResponse retryRun(@PathVariable("id") UUID id) {
        return runMapper.toRun(pipelineRunService.retryRun(id));
    }

    @GetMapping("/pipeline-runs/{id}/graph")
    public Object graph(@PathVariable("id") UUID id) {
        return pipelineRunService.listExecutions(id);
    }

    @GetMapping("/pipeline-runs/{id}/events")
    public RunEventsPageResponse events(
            @PathVariable("id") UUID id,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor
    ) {
        var page = pipelineRunService.listRunEvents(id, cursor, limit);
        return new RunEventsPageResponse(
                page.items().stream().map(runMapper::toExecution).toList(),
                page.nextCursor()
        );
    }
}

