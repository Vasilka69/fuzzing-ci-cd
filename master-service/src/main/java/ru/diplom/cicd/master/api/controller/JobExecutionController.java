package ru.diplom.cicd.master.api.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.run.JobExecutionResponse;
import ru.diplom.cicd.master.api.dto.run.LogPageResponse;
import ru.diplom.cicd.master.api.mapper.RunMapper;
import ru.diplom.cicd.master.opensearch.OpenSearchHistoryLogService;
import ru.diplom.cicd.master.repository.ArtifactRepository;
import ru.diplom.cicd.master.service.JobExecutionService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobExecutionService jobExecutionService;
    private final RunMapper runMapper;
    private final OpenSearchHistoryLogService openSearchHistoryLogService;
    private final ArtifactRepository artifactRepository;

    @GetMapping("/job-executions")
    public PageResponse<JobExecutionResponse> list(
            @RequestParam("pipelineRunId") UUID pipelineRunId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<JobExecutionResponse> items = jobExecutionService.list(pipelineRunId).stream()
                .map(runMapper::toExecution)
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @GetMapping("/job-executions/{id}")
    public JobExecutionResponse get(@PathVariable("id") UUID id) {
        return runMapper.toExecution(jobExecutionService.get(id));
    }

    @PostMapping("/job-executions/{id}/cancel")
    public JobExecutionResponse cancel(@PathVariable("id") UUID id) {
        return runMapper.toExecution(jobExecutionService.cancel(id));
    }

    @PostMapping("/job-executions/{id}/retry")
    public JobExecutionResponse retry(@PathVariable("id") UUID id) {
        return runMapper.toExecution(jobExecutionService.retry(id));
    }

    @GetMapping("/job-executions/{id}/logs")
    public LogPageResponse logs(
            @PathVariable("id") UUID id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "tail", required = false) Integer tail,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        var page = openSearchHistoryLogService.loadLogs(id, cursor, limit, tail, from, to);
        return new LogPageResponse(page.items(), page.nextCursor());
    }

    @GetMapping("/job-executions/{id}/artifacts")
    public Object artifacts(@PathVariable("id") UUID id) {
        return artifactRepository.findByJobExecutionId(id);
    }
}

