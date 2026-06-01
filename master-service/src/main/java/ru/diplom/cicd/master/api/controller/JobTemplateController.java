package ru.diplom.cicd.master.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
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
import ru.diplom.cicd.master.service.JobTemplateService;

@RestController
@RequestMapping("/api/v1/job-templates")
@RequiredArgsConstructor
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;

    @GetMapping
    public PageResponse<?> list(
            @RequestParam(name = "jobType", required = false) String jobType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<?> items = jobTemplateService.list(jobType);
        return PaginationHelper.paginate(items, page, size);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable("id") UUID id) {
        return jobTemplateService.get(id);
    }

    @PostMapping("/{id}/validate")
    public Map<String, Object> validate(@PathVariable("id") UUID id, @RequestBody(required = false) JsonNode params) {
        var result = jobTemplateService.validate(id, params);
        return Map.of("valid", result.valid(), "errors", result.errors());
    }
}

