package ru.diplom.cicd.master.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.api.dto.run.CreatePipelineRunRequest;
import ru.diplom.cicd.master.api.dto.trigger.CreateTriggerRequest;
import ru.diplom.cicd.master.api.dto.trigger.TriggerFireRequest;
import ru.diplom.cicd.master.api.dto.trigger.WebhookTriggerRequest;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;
import ru.diplom.cicd.master.domain.entity.TriggerEntity;
import ru.diplom.cicd.master.domain.entity.TriggerEventEntity;
import ru.diplom.cicd.master.domain.enums.Permission;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.PipelineRunRepository;
import ru.diplom.cicd.master.repository.TriggerEventRepository;
import ru.diplom.cicd.master.repository.TriggerRepository;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@Service
@RequiredArgsConstructor
public class TriggerService {

    private static final List<String> SUPPORTED_TRIGGER_TYPES = List.of("manual", "vcs_push", "schedule", "api");
    private static final List<String> ACTIVE_RUN_STATUSES = List.of("queued", "running", "waiting_approval", "canceling");

    private final TriggerRepository triggerRepository;
    private final TriggerEventRepository triggerEventRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineRunService pipelineRunService;
    private final PermissionService permissionService;
    private final UserContextService userContextService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @Transactional(readOnly = true)
    public List<TriggerEntity> list(UUID pipelineId) {
        if (pipelineId == null) {
            permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "system", null);
            return triggerRepository.findAll();
        }
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.VIEW, "pipeline", pipelineId);
        return triggerRepository.findByPipelineId(pipelineId);
    }

    @Transactional
    public TriggerEntity create(CreateTriggerRequest request) {
        UUID userId = userContextService.currentUserIdOrNull();
        permissionService.require(userId, Permission.EDIT, "pipeline", request.pipelineId());
        sensitiveDataSanitizer.requireNoInlineSecrets(request.config(), "config");

        String triggerType = normalizeTriggerType(request.triggerType());
        TriggerEntity trigger = TriggerEntity.builder()
                .id(UUID.randomUUID())
                .pipelineId(request.pipelineId())
                .name(request.name())
                .triggerType(triggerType)
                .config(request.config() == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(request.config()))
                .isActive(request.isActive() == null || request.isActive())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        TriggerEntity saved = triggerRepository.save(trigger);
        auditService.record(userId, "TRIGGER_CREATE", "trigger", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public PipelineRunEntity fire(UUID triggerId, TriggerFireRequest request) {
        TriggerEntity trigger = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "trigger_not_found", "Trigger not found"));
        requireActiveTrigger(trigger);
        permissionService.require(userContextService.currentUserIdOrNull(), Permission.RUN, "pipeline", trigger.getPipelineId());
        return processEvent(
                trigger,
                "api",
                request == null ? null : request.externalEventId(),
                request == null ? null : request.idempotencyKey(),
                null,
                null,
                request == null ? null : request.payload(),
                false
        );
    }

    @Transactional
    public PipelineRunEntity ingestWebhook(WebhookTriggerRequest request) {
        TriggerEntity trigger = resolveWebhookTrigger(request);
        requireActiveTrigger(trigger);
        return processEvent(
                trigger,
                request.eventSource() == null ? "vcs_webhook" : request.eventSource(),
                request.externalEventId(),
                request.idempotencyKey(),
                request.ref(),
                request.commitHash(),
                request.payload(),
                true
        );
    }

    @Transactional
    public PipelineRunEntity fireScheduled(TriggerEntity trigger, OffsetDateTime plannedFireTime, JsonNode payload) {
        String plannedKey = trigger.getId() + ":" + plannedFireTime.withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return fireScheduled(trigger, plannedKey, payload);
    }

    @Transactional
    public PipelineRunEntity fireScheduled(TriggerEntity trigger, String externalEventId, JsonNode payload) {
        if (!Boolean.TRUE.equals(trigger.getIsActive())) {
            return null;
        }
        String plannedKey = externalEventId;
        if (plannedKey == null || plannedKey.isBlank()) {
            plannedKey = trigger.getId() + ":" + OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (!hasScheduleCapacity(trigger)) {
            recordIgnoredScheduleEvent(trigger, plannedKey, payload);
            return null;
        }
        return processEvent(
                trigger,
                "schedule",
                plannedKey,
                plannedKey,
                null,
                null,
                payload,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<TriggerEntity> activeScheduleTriggers() {
        return triggerRepository.findByIsActiveTrue().stream()
                .filter(trigger -> "schedule".equalsIgnoreCase(trigger.getTriggerType()))
                .toList();
    }

    private PipelineRunEntity processEvent(
            TriggerEntity trigger,
            String eventSource,
            String externalEventId,
            String idempotencyKey,
            String ref,
            String commitHash,
            JsonNode payload,
            boolean systemInitiated
    ) {
        String payloadHash = shouldUsePayloadHash(externalEventId, idempotencyKey) ? buildPayloadHash(payload) : null;
        TriggerEventEntity duplicate = findDuplicate(eventSource, externalEventId, idempotencyKey, payloadHash);
        if (duplicate != null) {
            if (duplicate.getPipelineRunId() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "duplicate_trigger_event", "Trigger event already processed");
            }
            PipelineRunEntity existingRun = pipelineRunRepository.findById(duplicate.getPipelineRunId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "duplicate_trigger_event",
                            "Trigger event already processed"
                    ));
            return existingRun;
        }

        ObjectNode payloadNode = toObjectNode(sensitiveDataSanitizer.redact(payload));
        TriggerEventEntity event = TriggerEventEntity.builder()
                .id(UUID.randomUUID())
                .triggerId(trigger.getId())
                .pipelineId(trigger.getPipelineId())
                .eventSource(eventSource)
                .externalEventId(externalEventId)
                .idempotencyKey(idempotencyKey)
                .ref(ref)
                .commitHash(commitHash)
                .payloadHash(payloadHash)
                .payload(payloadNode)
                .status("received")
                .receivedAt(OffsetDateTime.now())
                .build();
        event = triggerEventRepository.save(event);

        try {
            PipelineRunEntity run = systemInitiated
                    ? pipelineRunService.createRunBySystem(trigger.getPipelineId(), "trigger", payloadNode)
                    : pipelineRunService.createRun(trigger.getPipelineId(), new CreatePipelineRunRequest("trigger", payloadNode));
            run.setTriggerId(trigger.getId());
            run = pipelineRunRepository.save(run);

            event.setStatus("accepted");
            event.setPipelineRunId(run.getId());
            event.setProcessedAt(OffsetDateTime.now());
            triggerEventRepository.save(event);
            return run;
        } catch (Exception ex) {
            event.setStatus("failed");
            event.setErrorMessage("trigger_processing_failed");
            event.setProcessedAt(OffsetDateTime.now());
            triggerEventRepository.save(event);
            throw ex;
        }
    }

    private TriggerEntity resolveWebhookTrigger(WebhookTriggerRequest request) {
        if (request.triggerId() != null) {
            TriggerEntity trigger = triggerRepository.findById(request.triggerId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "trigger_not_found", "Trigger not found"));
            if (!"vcs_push".equalsIgnoreCase(trigger.getTriggerType())
                    && !"api".equalsIgnoreCase(trigger.getTriggerType())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "trigger_type_invalid", "Trigger type does not support webhook events");
            }
            return trigger;
        }
        if (request.pipelineId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "pipeline_id_required", "pipelineId or triggerId is required");
        }
        return triggerRepository.findByPipelineId(request.pipelineId()).stream()
                .filter(TriggerEntity::getIsActive)
                .filter(trigger -> "vcs_push".equalsIgnoreCase(trigger.getTriggerType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "trigger_not_found", "Active webhook trigger not found"));
    }

    private TriggerEventEntity findDuplicate(String eventSource, String externalEventId, String idempotencyKey, String payloadHash) {
        if (externalEventId != null && !externalEventId.isBlank()) {
            var duplicate = triggerEventRepository.findByEventSourceAndExternalEventId(eventSource, externalEventId);
            if (duplicate.isPresent()) {
                return duplicate.get();
            }
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var duplicate = triggerEventRepository.findByEventSourceAndIdempotencyKey(eventSource, idempotencyKey);
            if (duplicate.isPresent()) {
                return duplicate.get();
            }
        }
        if (payloadHash != null && !payloadHash.isBlank()) {
            return triggerEventRepository.findByEventSourceAndPayloadHash(eventSource, payloadHash).orElse(null);
        }
        return null;
    }

    private void requireActiveTrigger(TriggerEntity trigger) {
        if (!Boolean.TRUE.equals(trigger.getIsActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "trigger_inactive", "Trigger is inactive");
        }
    }

    private String normalizeTriggerType(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "trigger_type_required", "Trigger type is required");
        }
        String normalized = value.trim().toLowerCase();
        if (!SUPPORTED_TRIGGER_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "trigger_type_invalid", "Unsupported trigger type");
        }
        return normalized;
    }

    private boolean hasScheduleCapacity(TriggerEntity trigger) {
        int maxParallelRuns = readPositiveInt(trigger.getConfig(), "max_parallel_runs", "maxParallelRuns", 1);
        if (maxParallelRuns <= 0) {
            return true;
        }
        long activeRuns = pipelineRunRepository.countByPipelineIdAndTriggerIdAndStatusIn(
                trigger.getPipelineId(),
                trigger.getId(),
                ACTIVE_RUN_STATUSES
        );
        return activeRuns < maxParallelRuns;
    }

    private void recordIgnoredScheduleEvent(TriggerEntity trigger, String plannedKey, JsonNode payload) {
        ObjectNode payloadNode = toObjectNode(sensitiveDataSanitizer.redact(payload));
        String payloadHash = shouldUsePayloadHash(plannedKey, plannedKey) ? buildPayloadHash(payloadNode) : null;
        if (findDuplicate("schedule", plannedKey, plannedKey, payloadHash) != null) {
            return;
        }
        TriggerEventEntity ignoredEvent = TriggerEventEntity.builder()
                .id(UUID.randomUUID())
                .triggerId(trigger.getId())
                .pipelineId(trigger.getPipelineId())
                .eventSource("schedule")
                .externalEventId(plannedKey)
                .idempotencyKey(plannedKey)
                .payloadHash(payloadHash)
                .payload(payloadNode)
                .status("ignored")
                .errorMessage("max_parallel_runs_exceeded")
                .receivedAt(OffsetDateTime.now())
                .processedAt(OffsetDateTime.now())
                .build();
        triggerEventRepository.save(ignoredEvent);
    }

    private ObjectNode toObjectNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("value", node);
        return wrapper;
    }

    private int readPositiveInt(JsonNode config, String snakeCaseField, String camelCaseField, int fallback) {
        if (config == null) {
            return fallback;
        }
        JsonNode snake = config.get(snakeCaseField);
        if (snake != null && snake.canConvertToInt() && snake.intValue() > 0) {
            return snake.intValue();
        }
        JsonNode camel = config.get(camelCaseField);
        if (camel != null && camel.canConvertToInt() && camel.intValue() > 0) {
            return camel.intValue();
        }
        return fallback;
    }

    private String buildPayloadHash(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        } catch (Exception ex) {
            String fallback = payload.toString();
            byte[] hash = digestSha256(fallback.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
    }

    private boolean shouldUsePayloadHash(String externalEventId, String idempotencyKey) {
        return (externalEventId == null || externalEventId.isBlank())
                && (idempotencyKey == null || idempotencyKey.isBlank());
    }

    private byte[] digestSha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
