package ru.diplom.cicd.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;

class ContractDtoTest {

    @Test
    void jobMessageCopiesMutableMaps() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshotUri", "storage://source.zip");
        Map<String, Object> params = new HashMap<>();
        params.put("command", "mvn test");
        Map<String, Object> secrets = new HashMap<>();
        secrets.put("refs", List.of("secret_ref"));

        JobMessage message = new JobMessage(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobType.BUILD,
                "build/maven",
                1,
                3,
                1800,
                null,
                new WorkspacePolicy("always", false),
                null,
                inputs,
                params,
                secrets,
                Instant.parse("2026-05-29T00:00:00Z"));

        inputs.put("lateMutation", true);

        Map<String, Object> dtoParams = message.params();

        assertEquals(1, message.schemaVersion());
        assertEquals(JobType.BUILD, message.jobType());
        assertEquals("build/maven", message.templatePath());
        assertTrue(message.resourceLimits().additionalLimits().isEmpty());
        assertFalse(message.inputs().containsKey("lateMutation"));
        assertThrows(UnsupportedOperationException.class, () -> dtoParams.put("unsafe", true));
    }

    @Test
    void executorEventMessageCopiesMutableCollections() {
        List<ArtifactDescriptor> artifacts = new ArrayList<>();
        artifacts.add(new ArtifactDescriptor(
                UUID.randomUUID(),
                "build_artifact",
                "app.jar",
                "storage://artifacts/app.jar",
                "application/java-archive",
                1024L,
                "sha256",
                Map.of("classifier", "release")));
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("tests", 42);

        ExecutorEventMessage message = new ExecutorEventMessage(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobType.BUILD,
                "build/maven",
                EventType.JOB_FINISHED,
                ExecutionStatus.SUCCESS,
                1,
                "build-worker-1",
                300000L,
                artifacts,
                metrics,
                "Сборка завершена успешно",
                null,
                null,
                Map.of());

        artifacts.clear();
        metrics.put("lateMutation", true);

        List<ArtifactDescriptor> dtoArtifacts = message.artifacts();
        assertEquals(1, dtoArtifacts.size());
        assertEquals(EventType.JOB_FINISHED, message.eventType());
        assertEquals(ExecutionStatus.SUCCESS, message.status());
        assertFalse(message.metrics().containsKey("lateMutation"));
        assertThrows(
                UnsupportedOperationException.class, dtoArtifacts::clear);
    }

    @Test
    void sandboxPolicyCopiesListsAndAdditionalData() {
        List<String> droppedCapabilities = new ArrayList<>(List.of("ALL"));
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("workspace", "writable temporary volume");

        SandboxPolicy policy = new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                droppedCapabilities,
                "RuntimeDefault",
                "none",
                List.of(),
                List.of(),
                false,
                additionalData);

        droppedCapabilities.add("NET_ADMIN");
        additionalData.put("lateMutation", true);

        List<String> capabilitiesDrop = policy.capabilitiesDrop();
        assertEquals(List.of("ALL"), capabilitiesDrop);
        assertFalse(policy.additionalData().containsKey("lateMutation"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilitiesDrop.add("SYS_ADMIN"));
    }
}
