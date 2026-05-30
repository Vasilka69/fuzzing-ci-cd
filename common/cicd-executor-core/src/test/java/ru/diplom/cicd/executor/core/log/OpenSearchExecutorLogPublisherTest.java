package ru.diplom.cicd.executor.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobType;

class OpenSearchExecutorLogPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishIndexesJobLogDocumentWithDeterministicId() throws Exception {
        CapturingOpenSearchLogSender sender = new CapturingOpenSearchLogSender();
        OpenSearchExecutorLogPublisher publisher = new OpenSearchExecutorLogPublisher(
                sender,
                new OpenSearchExecutorLogPublisherProperties(
                        URI.create("http://opensearch:9200/"),
                        "cicd-executor-events",
                        "build-service",
                        Duration.ofSeconds(5),
                        true),
                objectMapper,
                fixedClock());
        ExecutorEventMessage event = logEvent("line 1\nquoted \"value\"");

        publisher.publish(event).toCompletableFuture().join();

        String documentId = event.jobExecutionId() + "-" + event.messageId();
        assertEquals(
                URI.create("http://opensearch:9200/cicd-executor-events/_doc/" + documentId + "?refresh=true"),
                sender.uri);
        JsonNode json = objectMapper.readTree(sender.body);
        assertEquals(documentId, json.get("documentId").textValue());
        assertEquals("2026-05-30T09:00:00Z", json.get("ingestedAt").textValue());
        assertEquals("build-service", json.get("sourceService").textValue());
        assertEquals("build", json.get("jobType").textValue());
        assertEquals("JOB_LOG", json.get("eventType").textValue());
        assertEquals("RUNNING", json.get("status").textValue());
        assertEquals("line 1\nquoted \"value\"", json.get("logs").textValue());
        assertEquals(7, json.get("metrics").get("lines").intValue());
        assertEquals("stdout", json.get("additionalData").get("stream").textValue());
        assertEquals(true, json.get("additionalData").get("logOnly").booleanValue());
        assertFalse(json.has("event_type"));
    }

    @Test
    void defaultPropertiesMatchProjectOpenSearchIndex() {
        OpenSearchExecutorLogPublisherProperties properties = new OpenSearchExecutorLogPublisherProperties();

        assertEquals(
                URI.create("http://localhost:9200/cicd-executor-events/_doc/doc-1?refresh=true"),
                properties.documentUri("doc-1"));
        assertEquals("executor-core", properties.sourceService());
    }

    @Test
    void publishFailsWhenEventTypeIsNotJobLog() {
        CapturingOpenSearchLogSender sender = new CapturingOpenSearchLogSender();
        OpenSearchExecutorLogPublisher publisher = new OpenSearchExecutorLogPublisher(
                sender, new OpenSearchExecutorLogPublisherProperties(), objectMapper, fixedClock());
        ExecutorEventMessage event = event(EventType.JOB_RUNNING, "Лог запуска");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> publisher.publish(event));

        assertEquals("ExecutorLogPublisher принимает только события JOB_LOG", error.getMessage());
        assertEquals(0, sender.calls);
    }

    @Test
    void publishFailsWhenLogsAreBlank() {
        CapturingOpenSearchLogSender sender = new CapturingOpenSearchLogSender();
        OpenSearchExecutorLogPublisher publisher = new OpenSearchExecutorLogPublisher(
                sender, new OpenSearchExecutorLogPublisherProperties(), objectMapper, fixedClock());
        ExecutorEventMessage event = logEvent("   ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> publisher.publish(event));

        assertEquals("Лог-документ JOB_LOG должен содержать непустое поле logs", error.getMessage());
        assertEquals(0, sender.calls);
    }

    @Test
    void publishFailsFastWhenJobExecutionIdIsMissing() {
        OpenSearchExecutorLogPublisher publisher = new OpenSearchExecutorLogPublisher(
                new CapturingOpenSearchLogSender(),
                new OpenSearchExecutorLogPublisherProperties(),
                objectMapper,
                fixedClock());
        ExecutorEventMessage eventWithoutJobExecutionId = new ExecutorEventMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                UUID.fromString("00000000-0000-0000-0000-000000000014"),
                UUID.fromString("00000000-0000-0000-0000-000000000015"),
                UUID.fromString("00000000-0000-0000-0000-000000000016"),
                null,
                JobType.BUILD,
                "build/maven",
                EventType.JOB_LOG,
                ExecutionStatus.RUNNING,
                1,
                "build-worker-1",
                null,
                List.of(),
                Map.of(),
                "Лог сборки",
                null,
                "Лог сборки",
                Map.of());

        NullPointerException error =
                assertThrows(NullPointerException.class, () -> publisher.publish(eventWithoutJobExecutionId));

        assertEquals("Не задано обязательное поле logEvent.jobExecutionId", error.getMessage());
    }

    private ExecutorEventMessage logEvent(String logs) {
        return event(EventType.JOB_LOG, logs);
    }

    private ExecutorEventMessage event(EventType eventType, String logs) {
        return new ExecutorEventMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                JobType.BUILD,
                "build/maven",
                eventType,
                ExecutionStatus.RUNNING,
                1,
                "build-worker-1",
                3000L,
                List.of(),
                Map.of("lines", 7),
                "Лог сборки",
                null,
                logs,
                Map.of("stream", "stdout"));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-30T09:00:00Z"), ZoneOffset.UTC);
    }

    private static final class CapturingOpenSearchLogSender
            implements OpenSearchExecutorLogPublisher.OpenSearchLogSender {

        private int calls;
        private URI uri;
        private String body;

        @Override
        public CompletableFuture<Void> send(URI uri, String body) {
            calls++;
            this.uri = uri;
            this.body = body;
            return CompletableFuture.completedFuture(null);
        }
    }
}
