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

class SshBashDeploymentParametersTest {

    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000557/app.jar";

    @Test
    void fromReadsSshBashParams() {
        SshBashDeploymentParameters parameters = SshBashDeploymentParameters.from(deployJob(validParams()));

        assertEquals(ARTIFACT_URI, parameters.artifactUri());
        assertEquals("testing", parameters.environment());
        assertEquals("linux-target.test", parameters.target().host());
        assertEquals(2222, parameters.target().port());
        assertEquals("deploy", parameters.target().user());
        assertEquals("ssh-demo-credentials", parameters.target().credentialsRef());
        assertEquals(Path.of("/srv/apps/app.jar"), parameters.destinationPath());
        assertFalse(parameters.backupExisting());
        assertEquals(List.of("systemctl --user restart demo-app"), parameters.commands());
        assertEquals("ssh-release-2026-05-31-001", parameters.releaseId());
    }

    @Test
    void fromGeneratesReleaseIdWhenMissing() {
        SshBashDeploymentParameters parameters = SshBashDeploymentParameters.from(deployJob(Map.of(
                "deployment_type",
                "ssh_bash",
                "artifact_uri",
                ARTIFACT_URI,
                "target",
                validTarget(),
                "copy",
                Map.of("destination_path", "/srv/apps/app.jar"))));

        assertEquals("release-00000000-0000-0000-0000-000000000557", parameters.releaseId());
    }

    @Test
    void fromRejectsRelativeDestinationPath() {
        JobMessage job = deployJob(Map.of(
                "artifact_uri",
                ARTIFACT_URI,
                "target",
                validTarget(),
                "copy",
                Map.of("destination_path", "srv/apps/app.jar")));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> SshBashDeploymentParameters.from(job));

        assertEquals("copy.destination_path для ssh-bash должен быть absolute path", exception.getMessage());
    }

    @Test
    void fromRejectsUnsafeHost() {
        JobMessage job = deployJob(Map.of(
                "artifact_uri",
                ARTIFACT_URI,
                "target",
                Map.of("host", "bad host", "port", 22, "user", "deploy"),
                "copy",
                Map.of("destination_path", "/srv/apps/app.jar")));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> SshBashDeploymentParameters.from(job));

        assertEquals("target.host содержит недопустимые символы для SSH target", exception.getMessage());
    }

    @Test
    void fromRejectsNonSshBashTemplateDeploymentType() {
        JobMessage job = deployJob(Map.of(
                "deployment_type",
                "file_copy",
                "artifact_uri",
                ARTIFACT_URI,
                "target",
                validTarget(),
                "copy",
                Map.of("destination_path", "/srv/apps/app.jar")));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> SshBashDeploymentParameters.from(job));

        assertEquals("deployment_type не соответствует templatePath=deploy/ssh-bash", exception.getMessage());
    }

    @Test
    void fromRejectsPathTraversalReleaseId() {
        JobMessage job = deployJob(Map.of(
                "artifact_uri",
                ARTIFACT_URI,
                "target",
                validTarget(),
                "copy",
                Map.of("destination_path", "/srv/apps/app.jar"),
                "release_id",
                "release..prod"));

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> SshBashDeploymentParameters.from(job));

        assertEquals("release_id не должен содержать path traversal", exception.getMessage());
    }

    private Map<String, Object> validParams() {
        return Map.of(
                "deployment_type",
                "ssh_bash",
                "artifact_uri",
                ARTIFACT_URI,
                "environment",
                "testing",
                "target",
                validTarget(),
                "copy",
                Map.of("destination_path", "/srv/apps/app.jar", "backup_existing", false),
                "commands",
                List.of("systemctl --user restart demo-app"),
                "release_id",
                "ssh-release-2026-05-31-001");
    }

    private Map<String, Object> validTarget() {
        return Map.of(
                "host",
                "linux-target.test",
                "port",
                "2222",
                "user",
                "deploy",
                "credentials_ref",
                "ssh-demo-credentials");
    }

    private JobMessage deployJob(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000551"),
                UUID.fromString("00000000-0000-0000-0000-000000000552"),
                UUID.fromString("00000000-0000-0000-0000-000000000553"),
                UUID.fromString("00000000-0000-0000-0000-000000000554"),
                UUID.fromString("00000000-0000-0000-0000-000000000555"),
                UUID.fromString("00000000-0000-0000-0000-000000000556"),
                UUID.fromString("00000000-0000-0000-0000-000000000557"),
                JobType.DEPLOY,
                SshBashDeploymentParameters.TEMPLATE_PATH,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T09:30:00Z"));
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
