package ru.diplom.cicd.executor.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobType;

class OpenSearchLogDocumentSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void schemaIsPublishedAsResource() throws Exception {
        JsonNode schema = readResourceJson("/schemas/opensearch-log-document.schema.json");

        assertEquals("OpenSearchLogDocument", schema.get("title").textValue());
        assertEquals(
                1, schema.get("properties").get("schemaVersion").get("const").intValue());
        assertEquals(
                "JOB_LOG",
                schema.get("properties").get("eventType").get("const").textValue());
        assertTrue(texts(schema.get("required")).contains("logs"));
        assertFalse(schema.get("properties").has("artifacts"));
        assertFalse(schema.get("properties").has("error"));
    }

    @Test
    void serializedLogDocumentMatchesOpenSearchSchema() throws Exception {
        OpenSearchLogDocument document = OpenSearchLogDocument.from(
                logEvent(),
                "00000000-0000-0000-0000-000000000007-00000000-0000-0000-0000-000000000001",
                "build-service",
                Instant.parse("2026-05-30T09:00:00Z"));
        String body = objectMapper.writeValueAsString(document);

        assertTrue(validate(body).isEmpty());

        JsonNode json = objectMapper.readTree(body);
        assertEquals("JOB_LOG", json.get("eventType").textValue());
        assertEquals("build", json.get("jobType").textValue());
        assertEquals("stdout", json.get("additionalData").get("stream").textValue());
        assertTrue(json.get("additionalData").get("logOnly").booleanValue());
        assertFalse(json.has("event_type"));
        assertFalse(json.has("artifacts"));
        assertFalse(json.has("error"));
    }

    @Test
    void schemaRejectsServiceEventWithoutLogs() throws Exception {
        String body = """
                {
                  "documentId": "doc-1",
                  "ingestedAt": "2026-05-30T09:00:00Z",
                  "sourceService": "build-service",
                  "schemaVersion": 1,
                  "messageId": "00000000-0000-0000-0000-000000000001",
                  "correlationId": "00000000-0000-0000-0000-000000000002",
                  "pipelineRunId": "00000000-0000-0000-0000-000000000003",
                  "pipelineId": "00000000-0000-0000-0000-000000000004",
                  "stageId": "00000000-0000-0000-0000-000000000005",
                  "jobId": "00000000-0000-0000-0000-000000000006",
                  "jobExecutionId": "00000000-0000-0000-0000-000000000007",
                  "jobType": "build",
                  "templatePath": "build/maven",
                  "eventType": "JOB_RUNNING",
                  "status": "RUNNING",
                  "attempt": 1,
                  "workerId": "build-worker-1",
                  "durationMs": null,
                  "metrics": {},
                  "summary": "Job запущен",
                  "logs": null,
                  "additionalData": {"logOnly": true}
                }
                """;

        List<com.networknt.schema.Error> errors = validate(body);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream()
                .anyMatch(error -> error.getInstanceLocation().toString().equals("/eventType")));
        assertTrue(errors.stream()
                .anyMatch(error -> error.getInstanceLocation().toString().equals("/logs")));
    }

    private List<com.networknt.schema.Error> validate(String body) throws IOException {
        String schemaBody = readResourceText("/schemas/opensearch-log-document.schema.json");
        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        Schema schema = schemaRegistry.getSchema(schemaBody, InputFormat.JSON);
        return schema.validate(
                body,
                InputFormat.JSON,
                executionContext -> executionContext.executionConfig(
                        executionConfig -> executionConfig.formatAssertionsEnabled(true)));
    }

    private JsonNode readResourceJson(String resourcePath) throws IOException {
        return objectMapper.readTree(readResourceText(resourcePath));
    }

    private String readResourceText(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Не найдена JSON Schema: " + resourcePath);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ExecutorEventMessage logEvent() {
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
                EventType.JOB_LOG,
                ExecutionStatus.RUNNING,
                1,
                "build-worker-1",
                3000L,
                List.of(),
                Map.of("lines", 7),
                "Лог сборки",
                null,
                "line 1\nline 2",
                Map.of("stream", "stdout"));
    }

    private List<String> texts(JsonNode arrayNode) {
        return objectMapper.convertValue(
                arrayNode, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
