package ru.diplom.cicd.master.opensearch;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.master.config.AppProperties;

@Service
@RequiredArgsConstructor
public class OpenSearchHistoryLogService {

    private final OpenSearchApiClient openSearchApiClient;
    private final AppProperties appProperties;
    private final SearchAfterCursorCodec cursorCodec;

    public LogPage loadLogs(UUID jobExecutionId, String cursor, Integer limit, Integer tail) {
        return loadLogs(jobExecutionId, cursor, limit, tail, null, null);
    }

    public LogPage loadLogs(UUID jobExecutionId, String cursor, Integer limit, Integer tail, String from, String to) {
        int defaultSize = appProperties.getOpensearch().getLogsPageSize();
        int size = normalizeSize(limit, defaultSize);
        boolean tailMode = tail != null && tail > 0;
        if (tailMode) {
            size = Math.min(tail, 1000);
        }

        String sortOrder = tailMode ? "desc" : "asc";
        Map<String, Object> body = buildSearchBody(jobExecutionId, cursor, size, sortOrder, tailMode, from, to);
        Map<String, Object> response = openSearchApiClient.search(appProperties.getOpensearch().getIndex(), body);

        List<Map<String, Object>> hits = readHits(response);
        List<LogLine> lines = new ArrayList<>();
        List<Object> nextSearchAfter = List.of();

        for (Map<String, Object> hit : hits) {
            ExecutorEventDocument document = toDocument(hit);
            if (document == null || document.logs() == null) {
                continue;
            }
            lines.add(new LogLine(
                    document.documentId(),
                    document.logs(),
                    document.ingestedAt() == null ? null : document.ingestedAt().toString()
            ));
            List<Object> sortValues = asObjectList(hit.get("sort"));
            if (!sortValues.isEmpty()) {
                nextSearchAfter = sortValues;
            }
        }

        if (tailMode) {
            Collections.reverse(lines);
        }
        String nextCursor = nextSearchAfter.isEmpty() ? null : cursorCodec.encode(nextSearchAfter);
        return new LogPage(lines, nextCursor);
    }

    private Map<String, Object> buildSearchBody(
            UUID jobExecutionId,
            String cursor,
            int size,
            String order,
            boolean tailMode,
            String from,
            String to
    ) {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(Map.of("term", Map.of("jobExecutionId", jobExecutionId.toString())));
        must.add(Map.of("term", Map.of("eventType", "JOB_LOG")));

        Map<String, Object> range = new LinkedHashMap<>();
        if (from != null && !from.isBlank()) {
            range.put("gte", normalizeDate(from));
        }
        if (to != null && !to.isBlank()) {
            range.put("lte", normalizeDate(to));
        }
        if (!range.isEmpty()) {
            must.add(Map.of("range", Map.of("ingestedAt", range)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", size);
        body.put("query", Map.of("bool", Map.of("must", must)));
        body.put("sort", List.of(
                Map.of("ingestedAt", Map.of("order", order)),
                Map.of("documentId", Map.of("order", order))
        ));
        body.put("_source", List.of("documentId", "ingestedAt", "logs", "jobExecutionId", "eventType"));

        if (!tailMode) {
            List<Object> searchAfter = cursorCodec.decode(cursor);
            if (!searchAfter.isEmpty()) {
                body.put("search_after", searchAfter);
            }
        }
        return body;
    }

    private int normalizeSize(Integer requested, int fallback) {
        if (requested == null) {
            return fallback;
        }
        return Math.min(Math.max(requested, 1), 1000);
    }

    private String normalizeDate(String value) {
        try {
            return OffsetDateTime.parse(value).toString();
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readHits(Map<String, Object> response) {
        Map<String, Object> hits = asMap(response.get("hits"));
        Object nested = hits.get("hits");
        if (!(nested instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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

    private ExecutorEventDocument toDocument(Map<String, Object> hit) {
        if (hit == null || hit.isEmpty()) {
            return null;
        }
        Map<String, Object> source = asMap(hit.get("_source"));
        return new ExecutorEventDocument(
                stringValue(hit.get("_id")),
                parseOffsetDateTime(source.get("ingestedAt")),
                null,
                stringValue(source.get("eventType")),
                null,
                null,
                stringValue(source.get("jobExecutionId")),
                null,
                null,
                null,
                null,
                stringValue(source.get("logs")),
                null
        );
    }

    private OffsetDateTime parseOffsetDateTime(Object value) {
        String raw = stringValue(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record LogLine(String id, String message, String ts) {}
    public record LogPage(List<LogLine> items, String nextCursor) {}
}
