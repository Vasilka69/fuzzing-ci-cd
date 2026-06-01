package ru.diplom.cicd.master.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.pipeline.CreateFolderRequest;
import ru.diplom.cicd.master.api.dto.pipeline.FolderResponse;
import ru.diplom.cicd.master.service.FolderService;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public PageResponse<FolderResponse> list(
            @RequestParam(name = "parentId", required = false) UUID parentId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<FolderResponse> items = folderService.list(parentId).stream()
                .map(folder -> new FolderResponse(
                        folder.getId(),
                        folder.getName(),
                        folder.getDescription(),
                        folder.getParentId(),
                        folder.getCreatedAt(),
                        folder.getUpdatedAt()
                ))
                .toList();
        return PaginationHelper.paginate(items, page, size);
    }

    @PostMapping
    public FolderResponse create(@RequestBody @Valid CreateFolderRequest request) {
        var folder = folderService.create(request);
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getParentId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}

