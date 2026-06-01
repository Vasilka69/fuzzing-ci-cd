package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.entity.ExecutorEventCursorEntity;
import ru.diplom.cicd.master.opensearch.OpenSearchApiClient;
import ru.diplom.cicd.master.opensearch.OpenSearchExecutorEventPoller;
import ru.diplom.cicd.master.repository.ExecutorEventCursorRepository;
import ru.diplom.cicd.master.service.messaging.ExecutorEventService;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@ExtendWith(MockitoExtension.class)
class OpenSearchExecutorEventPollerTest {

    @Mock
    private OpenSearchApiClient openSearchApiClient;
    @Mock
    private ExecutorEventCursorRepository cursorRepository;
    @Mock
    private ExecutorEventService executorEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppProperties appProperties;
    private OpenSearchExecutorEventPoller poller;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getMessaging().setExecutorEventsTransport("opensearch");
        appProperties.getOpensearch().setIndex("cicd-executor-events");
        appProperties.getOpensearch().setCursorConsumerName("opensearch-event-poller");
        appProperties.getOpensearch().setPollBatchSize(200);
        poller = new OpenSearchExecutorEventPoller(
                appProperties,
                openSearchApiClient,
                cursorRepository,
                executorEventService,
                objectMapper
        );
    }

    @Test
    void usesStoredSearchAfterAndProcessesEvent() {
        ExecutorEventCursorEntity cursor = cursorWithMetadataSearchAfter(1700000000000L, "doc-7");
        when(cursorRepository.findByConsumerName("opensearch-event-poller")).thenReturn(Optional.of(cursor));
        when(openSearchApiClient.search(eq("cicd-executor-events"), any())).thenReturn(Map.of(
                "hits", Map.of(
                        "hits", List.of(hit("doc-8", "JOB_FINISHED", "SUCCESS", 1700000000100L, "doc-8"))
                )
        ));

        poller.poll();

        ArgumentCaptor<Map<String, Object>> searchRequest = ArgumentCaptor.forClass(Map.class);
        verify(openSearchApiClient).search(eq("cicd-executor-events"), searchRequest.capture());
        assertEquals(List.of(1700000000000L, "doc-7"), searchRequest.getValue().get("search_after"));

        ArgumentCaptor<ExecutorEventMessage> eventCaptor = ArgumentCaptor.forClass(ExecutorEventMessage.class);
        verify(executorEventService, times(1)).handle(
                eq("opensearch-event-poller"),
                eq("cicd-executor-events"),
                any(),
                eq("opensearch"),
                eq("doc-8"),
                eventCaptor.capture()
        );
        assertEquals("JOB_FINISHED", eventCaptor.getValue().eventType());
        assertEquals("SUCCESS", eventCaptor.getValue().status());
        assertNotNull(eventCaptor.getValue().jobExecutionId());

        verify(cursorRepository, times(1)).save(cursor);
        assertEquals("doc-8", cursor.getLastDocumentId());
        assertEquals(OffsetDateTime.ofInstant(Instant.ofEpochMilli(1700000000100L), ZoneOffset.UTC), cursor.getLastIngestedAt());
    }

    @Test
    void usesLastIngestedFieldsAsSearchAfterOnRestart() {
        ExecutorEventCursorEntity cursor = ExecutorEventCursorEntity.builder()
                .id(UUID.randomUUID())
                .consumerName("opensearch-event-poller")
                .eventSource("opensearch")
                .indexName("cicd-executor-events")
                .lastIngestedAt(OffsetDateTime.of(2026, 5, 31, 10, 0, 0, 0, ZoneOffset.UTC))
                .lastDocumentId("doc-prev")
                .metadata(objectMapper.createObjectNode())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(cursorRepository.findByConsumerName("opensearch-event-poller")).thenReturn(Optional.of(cursor));
        when(openSearchApiClient.search(eq("cicd-executor-events"), any())).thenReturn(Map.of(
                "hits", Map.of("hits", List.of())
        ));

        poller.poll();

        ArgumentCaptor<Map<String, Object>> searchRequest = ArgumentCaptor.forClass(Map.class);
        verify(openSearchApiClient).search(eq("cicd-executor-events"), searchRequest.capture());
        assertEquals(
                List.of(cursor.getLastIngestedAt().toInstant().toEpochMilli(), "doc-prev"),
                searchRequest.getValue().get("search_after")
        );
        verify(cursorRepository, never()).save(cursor);
        verify(executorEventService, never()).handle(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresJobLogEventsButMovesCursor() {
        ExecutorEventCursorEntity cursor = cursorWithMetadataSearchAfter(1699999999000L, "doc-old");
        when(cursorRepository.findByConsumerName("opensearch-event-poller")).thenReturn(Optional.of(cursor));
        when(openSearchApiClient.search(eq("cicd-executor-events"), any())).thenReturn(Map.of(
                "hits", Map.of(
                        "hits", List.of(hit("doc-log", "JOB_LOG", "RUNNING", 1700000000500L, "doc-log"))
                )
        ));

        poller.poll();

        verify(executorEventService, never()).handle(any(), any(), any(), any(), any(), any());
        verify(cursorRepository, times(1)).save(cursor);
        assertEquals("doc-log", cursor.getLastDocumentId());
        assertEquals(OffsetDateTime.ofInstant(Instant.ofEpochMilli(1700000000500L), ZoneOffset.UTC), cursor.getLastIngestedAt());
        assertEquals(
                List.of(1700000000500L, "doc-log"),
                objectMapper.convertValue(cursor.getMetadata().get("searchAfter"), List.class)
        );
    }

    private ExecutorEventCursorEntity cursorWithMetadataSearchAfter(long millis, String docId) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.set("searchAfter", objectMapper.valueToTree(List.of(millis, docId)));
        return ExecutorEventCursorEntity.builder()
                .id(UUID.randomUUID())
                .consumerName("opensearch-event-poller")
                .eventSource("opensearch")
                .indexName("cicd-executor-events")
                .metadata(metadata)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Map<String, Object> hit(String docId, String eventType, String status, long sortTsMillis, String sortDocId) {
        UUID pipelineRunId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID jobExecutionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        return Map.of(
                "_id", docId,
                "sort", List.of(sortTsMillis, sortDocId),
                "_source", Map.ofEntries(
                        Map.entry("schemaVersion", 1),
                        Map.entry("messageId", messageId.toString()),
                        Map.entry("correlationId", correlationId.toString()),
                        Map.entry("pipelineRunId", pipelineRunId.toString()),
                        Map.entry("pipelineId", pipelineId.toString()),
                        Map.entry("stageId", stageId.toString()),
                        Map.entry("jobId", jobId.toString()),
                        Map.entry("jobExecutionId", jobExecutionId.toString()),
                        Map.entry("jobType", "build"),
                        Map.entry("templatePath", "build/maven"),
                        Map.entry("eventType", eventType),
                        Map.entry("status", status),
                        Map.entry("attempt", 1),
                        Map.entry("workerId", "worker-1"),
                        Map.entry("startedAt", "2026-05-31T10:00:00Z"),
                        Map.entry("finishedAt", "2026-05-31T10:00:01Z"),
                        Map.entry("durationMs", 1000L),
                        Map.entry("artifacts", List.of()),
                        Map.entry("metrics", Map.of("duration", 1)),
                        Map.entry("summary", "ok"),
                        Map.entry("logs", "line"),
                        Map.entry("additionalData", Map.of("node", "n1")),
                        Map.entry("ingestedAt", "2026-05-31T10:00:10Z"),
                        Map.entry("documentId", docId)
                )
        );
    }
}
