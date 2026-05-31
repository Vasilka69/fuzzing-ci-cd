package ru.diplom.cicd.deploy.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

class SshBashDeploymentRunnerTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000657");
    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000657/app.jar";

    @TempDir
    private Path tempDir;

    @Test
    void deployDownloadsArtifactCopiesItWithScpAndRunsCommands() throws Exception {
        LocalStorageClient storageClient = storageClientWithArtifact();
        Path remoteRoot = tempDir.resolve("remote-root");
        Path scripts = tempDir.resolve("scripts");
        Files.createDirectories(scripts);
        Path ssh = fakeSsh(scripts.resolve("ssh"), tempDir.resolve("ssh.log"));
        Path scp = fakeScp(scripts.resolve("scp"), remoteRoot, tempDir.resolve("scp.log"));
        SshBashDeploymentRunner runner =
                new SshBashDeploymentRunner(storageClient, new LocalProcessRunner(), ssh.toString(), scp.toString());
        WorkspaceHandle workspace = new WorkspaceHandle(
                JOB_EXECUTION_ID, tempDir.resolve("workspace"), new WorkspacePolicy("always", false));
        SshBashDeploymentParameters parameters = SshBashDeploymentParameters.from(deployJob());

        SshBashDeploymentResult result = runner.deploy(deployJob(), workspace, parameters);

        Path expectedDestination = remoteRoot.resolve("srv/apps/app.jar");
        assertTrue(Files.isRegularFile(expectedDestination));
        assertEquals("deployable jar", Files.readString(expectedDestination));
        assertEquals(Files.size(expectedDestination), result.artifactBytes());
        assertEquals(StorageChecksums.sha256(expectedDestination), result.artifactChecksum());
        assertNotNull(result.backupResult());
        assertEquals(0, result.copyResult().exitCode());
        assertEquals(1, result.commandResults().size());
        assertEquals("ssh_file_exists", result.healthcheck().type());
        assertEquals("SUCCESS", result.healthcheck().status());
        assertTrue(result.healthcheck().passed());
        assertTrue(Files.readString(tempDir.resolve("scp.log")).contains("deploy@linux-target.test:/srv/apps/app.jar"));
        assertTrue(Files.readString(tempDir.resolve("ssh.log")).contains("printf deploy-ok"));
    }

    private LocalStorageClient storageClientWithArtifact() throws Exception {
        LocalStorageClient storageClient = new LocalStorageClient(tempDir.resolve("storage"));
        Path artifact = tempDir.resolve("app.jar");
        Files.writeString(artifact, "deployable jar");
        storageClient
                .upload(new StorageUploadRequest(
                        artifact,
                        "release-artifacts/%s/app.jar".formatted(JOB_EXECUTION_ID),
                        "release_artifact",
                        "app.jar",
                        "application/java-archive",
                        Map.of()))
                .toCompletableFuture()
                .join();
        return storageClient;
    }

    private JobMessage deployJob() {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000651"),
                UUID.fromString("00000000-0000-0000-0000-000000000652"),
                UUID.fromString("00000000-0000-0000-0000-000000000653"),
                UUID.fromString("00000000-0000-0000-0000-000000000654"),
                UUID.fromString("00000000-0000-0000-0000-000000000655"),
                UUID.fromString("00000000-0000-0000-0000-000000000656"),
                JOB_EXECUTION_ID,
                JobType.DEPLOY,
                SshBashDeploymentParameters.TEMPLATE_PATH,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of(
                        "deployment_type",
                        "ssh_bash",
                        "artifact_uri",
                        ARTIFACT_URI,
                        "target",
                        Map.of("host", "linux-target.test", "port", 2222, "user", "deploy"),
                        "copy",
                        Map.of("destination_path", "/srv/apps/app.jar"),
                        "commands",
                        List.of("printf deploy-ok")),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T10:30:00Z"));
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

    private Path fakeSsh(Path path, Path logPath) throws Exception {
        return executableScript(path, """
                #!/bin/sh
                set -eu
                LOG_FILE=%s
                echo "$*" >> "$LOG_FILE"
                exit 0
                """.formatted(singleQuote(logPath)));
    }

    private Path fakeScp(Path path, Path remoteRoot, Path logPath) throws Exception {
        return executableScript(path, """
                #!/bin/sh
                set -eu
                REMOTE_ROOT=%s
                LOG_FILE=%s
                echo "$*" >> "$LOG_FILE"
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    -P|-o) shift 2 ;;
                    *) break ;;
                  esac
                done
                SOURCE="$1"
                TARGET="$2"
                REMOTE_PATH="${TARGET#*:}"
                mkdir -p "$REMOTE_ROOT$(dirname "$REMOTE_PATH")"
                cp "$SOURCE" "$REMOTE_ROOT$REMOTE_PATH"
                """.formatted(singleQuote(remoteRoot), singleQuote(logPath)));
    }

    private Path executableScript(Path path, String content) throws Exception {
        Files.writeString(path, content);
        Files.setPosixFilePermissions(
                path,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return path;
    }

    private String singleQuote(Path path) {
        return "'" + path.toString().replace("'", "'\"'\"'") + "'";
    }
}
