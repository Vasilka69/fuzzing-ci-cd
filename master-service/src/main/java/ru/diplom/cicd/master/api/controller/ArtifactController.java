package ru.diplom.cicd.master.api.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.domain.entity.ArtifactEntity;
import ru.diplom.cicd.master.repository.ArtifactRepository;

@RestController
@RequestMapping("/api/v1/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactRepository artifactRepository;

    @GetMapping
    public PageResponse<ArtifactEntity> list(
            @RequestParam(name = "pipelineRunId", required = false) UUID pipelineRunId,
            @RequestParam(name = "jobExecutionId", required = false) UUID jobExecutionId,
            @RequestParam(name = "artifactType", required = false) String artifactType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<ArtifactEntity> items;
        if (jobExecutionId != null) {
            items = artifactRepository.findByJobExecutionId(jobExecutionId);
        } else if (pipelineRunId != null) {
            items = artifactRepository.findByPipelineRunId(pipelineRunId);
        } else {
            items = artifactRepository.findAll();
        }
        items = items.stream()
                .filter(item -> artifactType == null || artifactType.equalsIgnoreCase(String.valueOf(item.getArtifactType())))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable("id") UUID id) {
        return artifactRepository.findById(id).orElse(null);
    }

    @GetMapping("/{id}/download-url")
    public Map<String, String> downloadUrl(@PathVariable("id") UUID id) {
        return artifactRepository.findById(id)
                .map(artifact -> Map.of("downloadUrl", artifact.getUri()))
                .orElse(Map.of("downloadUrl", ""));
    }
}

