package ru.diplom.cicd.master.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.entity.ExecutorEventCursorEntity;
import ru.diplom.cicd.master.domain.enums.ExecutorEventType;
import ru.diplom.cicd.master.repository.ExecutorEventCursorRepository;
import ru.diplom.cicd.master.service.messaging.ExecutorEventService;
import ru.diplom.cicd.master.service.messaging.contract.ArtifactDescriptor;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorError;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchExecutorEventPoller {

    private final AppProperties appProperties;
    private final OpenSearchApiClient openSearchApiClient;
    private final ExecutorEventCursorRepository cursorRepository;
    private final ExecutorEventService executorEventService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.opensearch.poll-interval:5000}")
    @Transactional
    public void poll() {
        if (!"opensearch".equalsIgnoreCase(appProperties.getMessaging().getExecutorEventsTransport())) {
            return;
        }
        String consumer = appProperties.getOpensearch().getCursorConsumerName();
        ExecutorEventCursorEntity cursor = getOrCreateCursor(consumer);
        List<Object> searchAfter = readSearchAfter(cursor);

        Map<String, Object> requestBody = buildSearchBody(searchAfter);
        Map<String, Object> response;
        try {
            response = openSearchApiClient.search(appProperties.getOpensearch().getIndex(), requestBody);
        } catch (Exception ex) {
            log.error("OpenSearch poll failed", ex);
            return;
        }

        List<Map<String, Object>> hits = readHits(response);
        if (hits.isEmpty()) {
            return;
        }

        for (Map<String, Object> hit : hits) {
            String documentId = stringValue(hit.get("_id"));
            Map<String, Object> source = asMap(hit.get("_source"));
            ExecutorEventType eventType = ExecutorEventType.fromValue(stringValue(source.get("eventType")));
            if (eventType == ExecutorEventType.JOB_LOG) {
                updateCursorFromHit(cursor, hit);
                continue;
            }

            ExecutorEventMessage event = toEventMessage(source, documentId);
            if (event == null) {
                updateCursorFromHit(cursor, hit);
                continue;
            }

            try {
                executorEventService.handle(
                        appProperties.getOpensearch().getCursorConsumerName(),
                        appProperties.getOpensearch().getIndex(),
                        event.jobExecutionId() == null ? null : event.jobExecutionId().toString(),
                        "opensearch",
                        documentId,
                        event
                );
            } catch (Exception ex) {
                log.error("Cannot apply polled OpenSearch event docId={}", documentId, ex);
            }
            updateCursorFromHit(cursor, hit);
        }
        cursorRepository.save(cursor);
    }

    private Map<String, Object> buildSearchBody(List<Object> searchAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", appProperties.getOpensearch().getPollBatchSize());
        body.put("query", Map.of(
                "bool", Map.of(
                        "must_not", List.of(Map.of("term", Map.of("eventType", "JOB_LOG")))
                )
        ));
        body.put("sort", List.of(
                Map.of("ingestedAt", Map.of("order", "asc")),
                Map.of("documentId", Map.of("order", "asc"))
        ));
        body.put("_source", List.of(
                "schemaVersion", "messageId", "correlationId",
                "pipelineRunId", "pipelineId", "stageId", "jobId", "jobExecutionId",
                "jobType", "templatePath", "eventType", "status", "attempt", "workerId",
                "startedAt", "finishedAt", "durationMs", "artifacts", "metrics",
                "summary", "error", "logs", "additionalData", "ingestedAt", "documentId"
        ));
        if (!searchAfter.isEmpty()) {
            body.put("search_after", searchAfter);
        }
        return body;
    }

    private ExecutorEventCursorEntity getOrCreateCursor(String consumer) {
        return cursorRepository.findByConsumerName(consumer).orElseGet(() -> {
            ExecutorEventCursorEntity entity = ExecutorEventCursorEntity.builder()
                    .id(UUID.randomUUID())
                    .consumerName(consumer)
                    .eventSource("opensearch")
                    .indexName(appProperties.getOpensearch().getIndex())
                    .updatedAt(OffsetDateTime.now())
                    .metadata(objectMapper.createObjectNode())
                    .build();
            return cursorRepository.save(entity);
        });
    }

    private List<Object> readSearchAfter(ExecutorEventCursorEntity cursor) {
        if (cursor.getMetadata() != null && cursor.getMetadata().has("searchAfter")) {
            return objectMapper.convertValue(cursor.getMetadata().get("searchAfter"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
        }
        if (cursor.getLastIngestedAt() == null || cursor.getLastDocumentId() == null) {
            return List.of();
        }
        return List.of(cursor.getLastIngestedAt().toInstant().toEpochMilli(), cursor.getLastDocumentId());
    }

    private void updateCursorFromHit(ExecutorEventCursorEntity cursor, Map<String, Object> hit) {
        List<Object> sort = asObjectList(hit.get("sort"));
        if (sort.size() >= 2) {
            cursor.setLastIngestedAt(toOffsetDateTime(sort.get(0)));
            cursor.setLastDocumentId(stringValue(sort.get(1)));
            ObjectNode metadata = cursor.getMetadata() == null
                    ? objectMapper.createObjectNode()
                    : cursor.getMetadata().deepCopy();
            ArrayNode searchAfter = objectMapper.valueToTree(sort);
            metadata.set("searchAfter", searchAfter);
            cursor.setMetadata(metadata);
        } else {
            Map<String, Object> source = asMap(hit.get("_source"));
            cursor.setLastIngestedAt(toOffsetDateTime(source.get("ingestedAt")));
            cursor.setLastDocumentId(stringValue(source.get("documentId")));
        }
        cursor.setUpdatedAt(OffsetDateTime.now());
    }

    private ExecutorEventMessage toEventMessage(Map<String, Object> source, String documentId) {
        UUID pipelineRunId = parseUuid(source.get("pipelineRunId"));
        UUID pipelineId = parseUuid(source.get("pipelineId"));
        UUID jobId = parseUuid(source.get("jobId"));
        UUID jobExecutionId = parseUuid(source.get("jobExecutionId"));
        ExecutorEventType eventType = ExecutorEventType.fromValue(stringValue(source.get("eventType")));
        String status = stringValue(source.get("status"));
        String jobType = stringValue(source.get("jobType"));

        if (pipelineRunId == null || pipelineId == null || jobId == null || jobExecutionId == null
                || eventType == null || status == null || jobType == null) {
            log.debug("Skip OpenSearch doc {} due to missing required event fields", documentId);
            return null;
        }

        List<ArtifactDescriptor> artifacts = parseArtifacts(source.get("artifacts"));
        Map<String, Object> metrics = asMapOrNull(source.get("metrics"));
        Map<String, Object> additionalData = asMapOrNull(source.get("additionalData"));
        ExecutorError error = parseError(source.get("error"));

        return new ExecutorEventMessage(
                parseInt(source.get("schemaVersion"), 1),
                parseUuid(source.get("messageId"), parseUuid(documentId, UUID.randomUUID())),
                parseUuid(source.get("correlationId"), UUID.randomUUID()),
                pipelineRunId,
                pipelineId,
                parseUuid(source.get("stageId")),
                jobId,
                jobExecutionId,
                jobType,
                stringValue(source.get("templatePath")),
                eventType.name(),
                status,
                parseInt(source.get("attempt"), 1),
                stringValue(source.get("workerId")),
                toOffsetDateTime(source.get("startedAt")),
                toOffsetDateTime(source.get("finishedAt")),
                parseLong(source.get("durationMs")),
                artifacts,
                metrics,
                stringValue(source.get("summary")),
                error,
                stringValue(source.get("logs")),
                additionalData
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readHits(Map<String, Object> response) {
        Map<String, Object> hits = asMap(response.get("hits"));
        Object nested = hits.get("hits");
        if (!(nested instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private List<ArtifactDescriptor> parseArtifacts(Object rawArtifacts) {
        if (!(rawArtifacts instanceof List<?> list)) {
            return List.of();
        }
        List<ArtifactDescriptor> result = new ArrayList<>();
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> map = asMap(raw);
            result.add(new ArtifactDescriptor(
                    parseUuid(map.get("artifactId")),
                    stringValue(map.get("type")),
                    stringValue(map.get("name")),
                    stringValue(map.get("uri")),
                    parseLong(map.get("sizeBytes")),
                    stringValue(map.get("sha256")),
                    asMapOrNull(map.get("metadata"))
            ));
        }
        return result;
    }

    private ExecutorError parseError(Object rawError) {
        if (!(rawError instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> map = asMap(rawError);
        return new ExecutorError(
                stringValue(map.get("code")),
                stringValue(map.get("type")),
                parseBoolean(map.get("retryable")),
                stringValue(map.get("message")),
                asMapOrNull(map.get("details"))
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMapOrNull(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asObjectList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private UUID parseUuid(Object value) {
        return parseUuid(value, null);
    }

    private UUID parseUuid(Object value, UUID fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case OffsetDateTime dateTime -> {
                return dateTime;
            }
            case Number number -> {
                return Instant.ofEpochMilli(number.longValue()).atOffset(ZoneOffset.UTC);
            }
            default -> {
            }
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
