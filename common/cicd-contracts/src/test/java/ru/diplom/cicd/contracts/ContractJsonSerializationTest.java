package ru.diplom.cicd.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.error.ExecutorError;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;

class ContractJsonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void jobMessageSerializesAsCamelCaseJsonContract() throws Exception {
        JobMessage message = new JobMessage(
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
                1,
                3,
                1800,
                new ResourceLimits(1000, 536870912L, 1073741824L, 64, Map.of()),
                new WorkspacePolicy("always", false),
                new SandboxPolicy(
                        false,
                        false,
                        true,
                        false,
                        true,
                        List.of(),
                        List.of("ALL"),
                        "RuntimeDefault",
                        "none",
                        List.of(),
                        List.of(),
                        false,
                        Map.of()),
                Map.of("snapshotUri", "storage://source.zip"),
                Map.of("goal", "package"),
                Map.of("refs", List.of("maven_token")),
                Instant.parse("2026-05-29T00:00:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertEquals(1, json.get("schemaVersion").intValue());
        assertEquals("build", json.get("jobType").textValue());
        assertEquals("build/maven", json.get("templatePath").textValue());
        assertEquals("2026-05-29T00:00:00Z", json.get("createdAt").textValue());
        assertTrue(json.has("timeoutSeconds"));
        assertTrue(json.get("resourceLimits").has("memoryBytes"));
        assertTrue(json.get("workspacePolicy").has("preserveOnFailure"));
        assertFalse(json.has("timeout_seconds"));
        assertFalse(json.has("job_type"));
    }

    @Test
    void executorEventMessageSerializesEnumsAndNullableLogs() throws Exception {
        ExecutorEventMessage message = new ExecutorEventMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                UUID.fromString("00000000-0000-0000-0000-000000000014"),
                UUID.fromString("00000000-0000-0000-0000-000000000015"),
                UUID.fromString("00000000-0000-0000-0000-000000000016"),
                UUID.fromString("00000000-0000-0000-0000-000000000017"),
                JobType.BUILD,
                "build/maven",
                EventType.JOB_FINISHED,
                ExecutionStatus.SUCCESS,
                1,
                "build-worker-1",
                300000L,
                List.of(new ArtifactDescriptor(
                        UUID.fromString("00000000-0000-0000-0000-000000000018"),
                        "build_artifact",
                        "app.jar",
                        "storage://artifacts/app.jar",
                        "application/java-archive",
                        1024L,
                        "sha256",
                        Map.of())),
                Map.of("tests", 42),
                "Сборка завершена успешно",
                null,
                null,
                Map.of());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertEquals("build", json.get("jobType").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertTrue(json.get("logs").isNull());
        assertEquals(
                "build_artifact",
                json.get("artifacts").get(0).get("artifactType").textValue());
        assertFalse(json.has("event_type"));
    }

    @Test
    void deserializationIgnoresUnknownFieldsWhereContractAllowsCompatibility() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "messageId": "00000000-0000-0000-0000-000000000001",
                  "correlationId": "00000000-0000-0000-0000-000000000002",
                  "pipelineRunId": "00000000-0000-0000-0000-000000000003",
                  "pipelineId": "00000000-0000-0000-0000-000000000004",
                  "stageId": "00000000-0000-0000-0000-000000000005",
                  "jobId": "00000000-0000-0000-0000-000000000006",
                  "jobExecutionId": "00000000-0000-0000-0000-000000000007",
                  "jobType": "script",
                  "templatePath": "script/bash",
                  "attempt": 1,
                  "maxAttempts": 1,
                  "timeoutSeconds": 60,
                  "resourceLimits": {},
                  "workspacePolicy": {"cleanup": "always", "preserveOnFailure": false},
                  "sandboxPolicy": {"unknownSandboxFlag": true},
                  "inputs": {},
                  "params": {},
                  "secrets": {"refs": []},
                  "createdAt": "2026-05-29T00:00:00Z",
                  "futureField": "ignored"
                }
                """;

        JobMessage message = objectMapper.readValue(json, JobMessage.class);

        assertEquals(JobType.SCRIPT, message.jobType());
        assertEquals("script/bash", message.templatePath());
        assertNotNull(message.sandboxPolicy());
        assertTrue(message.sandboxPolicy().additionalData().isEmpty());
    }

    @Test
    void executorErrorDeserializesWireErrorType() throws Exception {
        String json = """
                {
                  "type": "security_error",
                  "code": "sandbox_policy_denied",
                  "message": "Запрошен запрещенный sandbox mode",
                  "details": null,
                  "metadata": {}
                }
                """;

        ExecutorError error = objectMapper.readValue(json, ExecutorError.class);

        assertEquals(ErrorType.SECURITY_ERROR, error.type());
    }

    @Test
    void jsonSchemasArePublishedAsResources() throws Exception {
        JsonNode jobSchema = readSchema("/schemas/job-message.schema.json");
        JsonNode eventSchema = readSchema("/schemas/executor-event-message.schema.json");

        assertEquals("JobMessage", jobSchema.get("title").textValue());
        assertEquals("ExecutorEventMessage", eventSchema.get("title").textValue());
        assertEquals(
                1, jobSchema.get("properties").get("schemaVersion").get("const").intValue());
        assertEquals(
                1,
                eventSchema.get("properties").get("schemaVersion").get("const").intValue());
        assertIterableEquals(
                List.of("vcs", "storage", "build", "fuzzing", "deploy", "script"),
                texts(jobSchema.get("$defs").get("jobType").get("enum")));
        assertIterableEquals(
                List.of(
                        "JOB_ACCEPTED",
                        "JOB_RUNNING",
                        "JOB_PROGRESS",
                        "JOB_ARTIFACT",
                        "JOB_LOG",
                        "JOB_FINISHED",
                        "JOB_SKIPPED",
                        "JOB_CANCELED",
                        "JOB_HEARTBEAT"),
                texts(eventSchema.get("$defs").get("eventType").get("enum")));
        assertTrue(texts(eventSchema.get("required")).contains("logs"));
        assertIterableEquals(
                List.of("string", "null"),
                texts(eventSchema.get("properties").get("logs").get("type")));
    }

    private JsonNode readSchema(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Не найдена JSON Schema: " + resourcePath);
            return objectMapper.readTree(inputStream);
        }
    }

    private List<String> texts(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }
}
