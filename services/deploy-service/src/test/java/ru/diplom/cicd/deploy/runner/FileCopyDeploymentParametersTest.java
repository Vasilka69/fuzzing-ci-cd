package ru.diplom.cicd.deploy.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class FileCopyDeploymentParametersTest {

    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000507/app.jar";

    @Test
    void fromReadsFileCopyParams() {
        FileCopyDeploymentParameters parameters = FileCopyDeploymentParameters.from(deployJob(Map.of(
                "deployment_type",
                "file_copy",
                "artifact_uri",
                ARTIFACT_URI,
                "environment",
                "testing",
                "target",
                Map.of("connection_ref", "local-demo-target", "destination_path", "apps/app.jar"),
                "verify_checksum",
                false,
                "release_id",
                "release-2026-05-31-001")));

        assertEquals(ARTIFACT_URI, parameters.artifactUri());
        assertEquals("testing", parameters.environment());
        assertEquals(Path.of("apps/app.jar"), parameters.destinationPath());
        assertFalse(parameters.verifyChecksum());
        assertEquals("release-2026-05-31-001", parameters.releaseId());
        assertEquals("local-demo-target", parameters.connectionRef());
    }

    @Test
    void fromRejectsNonDeployJobType() {
        JobMessage job = job(JobType.BUILD, FileCopyDeploymentParameters.TEMPLATE_PATH, validParams());

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> FileCopyDeploymentParameters.from(job));

        assertEquals("Deploy-сервис принимает только jobType=deploy", exception.getMessage());
    }

    @Test
    void fromRejectsParentTraversalDestinationPath() {
        JobMessage job = deployJob(
                Map.of("artifact_uri", ARTIFACT_URI, "target", Map.of("destination_path", "../outside/app.jar")));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> FileCopyDeploymentParameters.from(job));

        assertEquals(
                "target.destination_path должен быть относительным путем внутри deploy target root",
                exception.getMessage());
    }

    @Test
    void fromRejectsNonStorageArtifactUri() {
        JobMessage job = deployJob(Map.of(
                "artifact_uri", "https://example.test/app.jar", "target", Map.of("destination_path", "apps/app.jar")));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> FileCopyDeploymentParameters.from(job));

        assertEquals("artifact_uri должен использовать схему storage://", exception.getMessage());
    }

    private Map<String, Object> validParams() {
        return Map.of("artifact_uri", ARTIFACT_URI, "target", Map.of("destination_path", "apps/app.jar"));
    }

    private JobMessage deployJob(Map<String, Object> params) {
        return job(JobType.DEPLOY, FileCopyDeploymentParameters.TEMPLATE_PATH, params);
    }

    private JobMessage job(JobType jobType, String templatePath, Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                UUID.fromString("00000000-0000-0000-0000-000000000502"),
                UUID.fromString("00000000-0000-0000-0000-000000000503"),
                UUID.fromString("00000000-0000-0000-0000-000000000504"),
                UUID.fromString("00000000-0000-0000-0000-000000000505"),
                UUID.fromString("00000000-0000-0000-0000-000000000506"),
                UUID.fromString("00000000-0000-0000-0000-000000000507"),
                jobType,
                templatePath,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T09:00:00Z"));
    }

    private SandboxPolicy safeSandboxPolicy() {
        return new SandboxPolicy(
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
                Map.of());
    }
}
