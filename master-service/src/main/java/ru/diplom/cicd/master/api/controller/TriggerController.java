package ru.diplom.cicd.master.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.diplom.cicd.master.api.PaginationHelper;
import ru.diplom.cicd.master.api.dto.PageResponse;
import ru.diplom.cicd.master.api.dto.run.PipelineRunResponse;
import ru.diplom.cicd.master.api.dto.trigger.CreateTriggerRequest;
import ru.diplom.cicd.master.api.dto.trigger.TriggerFireRequest;
import ru.diplom.cicd.master.api.dto.trigger.WebhookTriggerRequest;
import ru.diplom.cicd.master.api.mapper.RunMapper;
import ru.diplom.cicd.master.domain.entity.TriggerEntity;
import ru.diplom.cicd.master.service.TriggerService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TriggerController {

    private final TriggerService triggerService;
    private final RunMapper runMapper;

    @GetMapping("/triggers")
    public PageResponse<TriggerEntity> list(
            @RequestParam(name = "pipelineId", required = false) UUID pipelineId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        List<TriggerEntity> items = triggerService.list(pipelineId);
        return PaginationHelper.paginate(items, page, size);
    }

    @PostMapping("/triggers")
    public TriggerEntity create(@Valid @RequestBody CreateTriggerRequest request) {
        return triggerService.create(request);
    }

    @PostMapping("/triggers/{id}/fire")
    public PipelineRunResponse fire(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) TriggerFireRequest request
    ) {
        TriggerFireRequest effectiveRequest = request == null
                ? new TriggerFireRequest(null, idempotencyKey, null)
                : new TriggerFireRequest(
                        request.externalEventId(),
                        request.idempotencyKey() == null ? idempotencyKey : request.idempotencyKey(),
                        request.payload()
                );
        return runMapper.toRun(triggerService.fire(id, effectiveRequest));
    }

    @PostMapping("/triggers/webhook")
    public PipelineRunResponse webhook(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody WebhookTriggerRequest request
    ) {
        WebhookTriggerRequest effectiveRequest = request.idempotencyKey() == null
                ? new WebhookTriggerRequest(
                        request.triggerId(),
                        request.pipelineId(),
                        request.eventSource(),
                        request.externalEventId(),
                        idempotencyKey,
                        request.ref(),
                        request.commitHash(),
                        request.payload()
                )
                : request;
        return runMapper.toRun(triggerService.ingestWebhook(effectiveRequest));
    }

    @PostMapping("/triggers/vcs/{triggerId}")
    public PipelineRunResponse vcsWebhook(
            @PathVariable("triggerId") UUID triggerId,
            @RequestHeader(name = "X-Event-Id", required = false) String externalEventId,
            @RequestHeader(name = "X-Commit-Hash", required = false) String commitHash,
            @RequestHeader(name = "X-Git-Ref", required = false) String ref,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Event-Source", required = false, defaultValue = "vcs_webhook") String eventSource,
            @RequestBody(required = false) JsonNode payload
    ) {
        WebhookTriggerRequest request = new WebhookTriggerRequest(
                triggerId,
                null,
                eventSource,
                externalEventId,
                idempotencyKey,
                ref,
                commitHash,
                payload
        );
        return runMapper.toRun(triggerService.ingestWebhook(request));
    }
}

