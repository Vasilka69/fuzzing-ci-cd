package ru.diplom.cicd.master.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.service.messaging.ExecutorEventService;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class ExecutorEventController {

    private final ExecutorEventService executorEventService;

    @PostMapping("/executor-events")
    public void ingest(@RequestBody ExecutorEventMessage event) {
        executorEventService.handle("internal-api-consumer", "internal", event.jobExecutionId().toString(), "api", null, event);
    }
}
