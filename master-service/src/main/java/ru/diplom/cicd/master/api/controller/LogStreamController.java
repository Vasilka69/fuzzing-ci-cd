package ru.diplom.cicd.master.api.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.sse.SseSessionRegistry;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogStreamController {

    private final SseSessionRegistry sseSessionRegistry;
    private final AppProperties appProperties;

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam(name = "pipelineRunId", required = false) UUID pipelineRunId,
            @RequestParam(name = "jobId", required = false) UUID jobId,
            @RequestParam(name = "jobExecutionId", required = false) UUID jobExecutionId
    ) {
        long timeoutMs = appProperties.getSse().getEmitterTimeout().toMillis();
        return sseSessionRegistry.register(pipelineRunId, jobId, jobExecutionId, timeoutMs);
    }
}

