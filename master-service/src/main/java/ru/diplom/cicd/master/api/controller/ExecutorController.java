package ru.diplom.cicd.master.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.domain.entity.ExecutorHeartbeatEntity;
import ru.diplom.cicd.master.repository.ExecutorHeartbeatRepository;

@RestController
@RequestMapping("/api/v1/executors")
@RequiredArgsConstructor
public class ExecutorController {

    private final ExecutorHeartbeatRepository executorHeartbeatRepository;

    @GetMapping
    public PageResponse<ExecutorHeartbeatEntity> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return PaginationHelper.paginate(executorHeartbeatRepository.findAll(), page, size);
    }
}

