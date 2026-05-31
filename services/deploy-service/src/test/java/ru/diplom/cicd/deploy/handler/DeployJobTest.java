package ru.diplom.cicd.deploy.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentParameters;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentRunner;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;

class DeployJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000707/app.jar";

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleFileCopyJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        LocalStorageClient storageClient = storageClientWithArtifact();
        Path targetRoot = tempDir.resolve("deploy-target");
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "deploy-test-worker-1");
        DeployJob job = deployJob(storageClient, targetRoot, new LocalProcessRunner());

        ExecutorEventMessage finishedEvent = handler.handle(deployJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Deploy file-copy завершен успешно", finishedEvent.summary());
        assertEquals("file_copy", finishedEvent.additionalData().get("deploymentType"));
        assertEquals(ARTIFACT_URI, finishedEvent.additionalData().get("artifactUri"));
        assertEquals("testing", finishedEvent.additionalData().get("environment"));
        assertEquals("apps/app.jar", finishedEvent.additionalData().get("relativeDestinationPath"));
        assertEquals("release-2026-05-31-001", finishedEvent.additionalData().get("releaseId"));
        assertEquals("local-demo-target", finishedEvent.additionalData().get("connectionRef"));
        assertEquals(14L, finishedEvent.additionalData().get("bytesCopied"));
        assertEquals(true, finishedEvent.additionalData().get("checksumVerified"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        Path deployedArtifact = targetRoot.resolve("apps/app.jar");
        assertTrue(Files.isRegularFile(deployedArtifact));
        assertEquals("deployable jar", Files.readString(deployedArtifact));
        String checksum = StorageChecksums.sha256(deployedArtifact);
        assertEquals(checksum, finishedEvent.additionalData().get("deployedArtifactChecksum"));

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Deploy file-copy скачал artifact из storage"));
        assertTrue(logEvent.logs().contains("Deploy file-copy скопировал artifact в target path"));
        assertTrue(logEvent.logs().contains(checksum));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("deploy", json.get("jobType").textValue());
        assertEquals("deploy/file-copy", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(
                "file_copy", json.get("additionalData").get("deploymentType").textValue());
        assertEquals(ARTIFACT_URI, json.get("additionalData").get("artifactUri").textValue());
        assertEquals("testing", json.get("additionalData").get("environment").textValue());
        assertEquals(
                "apps/app.jar",
                json.get("additionalData").get("relativeDestinationPath").textValue());
        assertEquals(14L, json.get("additionalData").get("bytesCopied").longValue());
        assertEquals(
                checksum,
                json.get("additionalData").get("deployedArtifactChecksum").textValue());
        assertTrue(json.get("additionalData").get("checksumVerified").booleanValue());
        assertEquals(
                "release-2026-05-31-001",
                json.get("additionalData").get("releaseId").textValue());
        assertEquals(
                "local-demo-target",
                json.get("additionalData").get("connectionRef").textValue());
        assertEquals(0, json.get("artifacts").size());
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleFileCopyValidationErrorReturnsFailedEvent() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        DeployJob job = deployJob(storageClientWithArtifact(), tempDir.resolve("target"), new LocalProcessRunner());
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "deploy-test-worker-1");

        ExecutorEventMessage finishedEvent = handler.handle(
                deployJob(Map.of(
                        "artifact_uri", ARTIFACT_URI, "target", Map.of("destination_path", "../outside/app.jar"))),
                job);

        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.FAILED, finishedEvent.status());
        assertEquals(ErrorType.VALIDATION_ERROR, finishedEvent.error().type());
        assertTrue(finishedEvent.summary().contains("target.destination_path"));
        assertTrue(logPublisher.events.isEmpty());
    }

    @SuppressWarnings("java:S5961")
    @Test
    void handleSshBashJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        LocalStorageClient storageClient = storageClientWithArtifact();
        Path remoteRoot = tempDir.resolve("remote-root");
        Path scripts = tempDir.resolve("fake-ssh");
        Files.createDirectories(scripts);
        Path ssh = fakeSsh(scripts.resolve("ssh"), tempDir.resolve("ssh.log"));
        Path scp = fakeScp(scripts.resolve("scp"), remoteRoot, tempDir.resolve("scp.log"));
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("ssh-workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "deploy-test-worker-1");
        DeployJob job = deployJob(
                storageClient,
                tempDir.resolve("unused-target"),
                new LocalProcessRunner(),
                ssh.toString(),
                scp.toString());

        ExecutorEventMessage finishedEvent = handler.handle(sshBashJob(), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Deploy ssh-bash завершен успешно", finishedEvent.summary());
        assertEquals("ssh_bash", finishedEvent.additionalData().get("deploymentType"));
        assertEquals(ARTIFACT_URI, finishedEvent.additionalData().get("artifactUri"));
        assertEquals("testing", finishedEvent.additionalData().get("environment"));
        assertEquals("linux-target.test", finishedEvent.additionalData().get("targetHost"));
        assertEquals(2222, finishedEvent.additionalData().get("targetPort"));
        assertEquals("deploy", finishedEvent.additionalData().get("targetUser"));
        assertEquals("/srv/apps/app.jar", finishedEvent.additionalData().get("destinationPath"));
        assertEquals(
                "ssh-release-2026-05-31-001", finishedEvent.additionalData().get("releaseId"));
        assertEquals("ssh-demo-credentials", finishedEvent.additionalData().get("credentialsRef"));
        assertEquals(14L, finishedEvent.additionalData().get("bytesCopied"));
        assertEquals(false, finishedEvent.additionalData().get("checksumVerified"));
        assertEquals(1, finishedEvent.additionalData().get("commandCount"));
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        Path deployedArtifact = remoteRoot.resolve("srv/apps/app.jar");
        assertTrue(Files.isRegularFile(deployedArtifact));
        assertEquals("deployable jar", Files.readString(deployedArtifact));
        String checksum = StorageChecksums.sha256(deployedArtifact);
        assertEquals(checksum, finishedEvent.additionalData().get("deployedArtifactChecksum"));

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Deploy ssh-bash скачал artifact из storage"));
        assertTrue(logEvent.logs().contains("Deploy ssh-bash скопировал artifact через scp"));
        assertTrue(logEvent.logs().contains(checksum));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("deploy/ssh-bash", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(
                "ssh_bash", json.get("additionalData").get("deploymentType").textValue());
        assertEquals(
                "/srv/apps/app.jar",
                json.get("additionalData").get("destinationPath").textValue());
        assertEquals(1, json.get("additionalData").get("commandCount").intValue());
        assertTrue(json.get("logs").isNull());
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
        return deployJob(Map.of(
                "deployment_type",
                "file_copy",
                "artifact_uri",
                ARTIFACT_URI,
                "environment",
                "testing",
                "target",
                Map.of("connection_ref", "local-demo-target", "destination_path", "apps/app.jar"),
                "verify_checksum",
                true,
                "release_id",
                "release-2026-05-31-001"));
    }

    private JobMessage sshBashJob() {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000711"),
                UUID.fromString("00000000-0000-0000-0000-000000000712"),
                UUID.fromString("00000000-0000-0000-0000-000000000713"),
                UUID.fromString("00000000-0000-0000-0000-000000000714"),
                UUID.fromString("00000000-0000-0000-0000-000000000715"),
                UUID.fromString("00000000-0000-0000-0000-000000000716"),
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
                        "environment",
                        "testing",
                        "target",
                        Map.of(
                                "host",
                                "linux-target.test",
                                "port",
                                2222,
                                "user",
                                "deploy",
                                "credentials_ref",
                                "ssh-demo-credentials"),
                        "copy",
                        Map.of("destination_path", "/srv/apps/app.jar", "backup_existing", true),
                        "commands",
                        List.of("printf deploy-ok"),
                        "release_id",
                        "ssh-release-2026-05-31-001"),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T12:00:00Z"));
    }

    private JobMessage deployJob(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                UUID.fromString("00000000-0000-0000-0000-000000000703"),
                UUID.fromString("00000000-0000-0000-0000-000000000704"),
                UUID.fromString("00000000-0000-0000-0000-000000000705"),
                UUID.fromString("00000000-0000-0000-0000-000000000706"),
                JOB_EXECUTION_ID,
                JobType.DEPLOY,
                FileCopyDeploymentParameters.TEMPLATE_PATH,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T11:00:00Z"));
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

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private DeployJob deployJob(LocalStorageClient storageClient, Path targetRoot, ProcessRunner processRunner) {
        return deployJob(storageClient, targetRoot, processRunner, "ssh", "scp");
    }

    private DeployJob deployJob(
            LocalStorageClient storageClient,
            Path targetRoot,
            ProcessRunner processRunner,
            String sshExecutable,
            String scpExecutable) {
        return new DeployJob(
                new FileCopyDeploymentRunner(storageClient, targetRoot),
                new SshBashDeploymentRunner(storageClient, processRunner, sshExecutable, scpExecutable));
    }

    private Path fakeSsh(Path path, Path logPath) throws Exception {
        return executableScript(path, """
                #!/bin/sh
                set -eu
                LOG_FILE=%s
                echo "$*" >> "$LOG_FILE"
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    -p|-o) shift 2 ;;
                    *) break ;;
                  esac
                done
                shift
                if [ "$#" -ge 3 ] && [ "$1" = "bash" ] && [ "$2" = "-lc" ]; then
                  echo "remote command: $3"
                fi
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

    private static final class CapturingEventPublisher implements ExecutorEventPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class CapturingLogPublisher implements ExecutorLogPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage logEvent) {
            events.add(logEvent);
            return CompletableFuture.completedFuture(null);
        }
    }
}
