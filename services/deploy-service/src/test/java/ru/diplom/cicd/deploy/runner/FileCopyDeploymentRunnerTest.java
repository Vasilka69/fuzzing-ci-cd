package ru.diplom.cicd.deploy.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

class FileCopyDeploymentRunnerTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000607");
    private static final String ARTIFACT_URI =
            "storage://release-artifacts/00000000-0000-0000-0000-000000000607/app.jar";

    @TempDir
    private Path tempDir;

    @Test
    void deployDownloadsArtifactAndCopiesItInsideTargetRoot() throws Exception {
        LocalStorageClient storageClient = storageClientWithArtifact();
        FileCopyDeploymentRunner runner = new FileCopyDeploymentRunner(storageClient, tempDir.resolve("deploy-target"));
        WorkspaceHandle workspace = new WorkspaceHandle(
                JOB_EXECUTION_ID, tempDir.resolve("workspace"), new WorkspacePolicy("always", false));
        FileCopyDeploymentParameters parameters = FileCopyDeploymentParameters.from(deployJob());

        FileCopyDeploymentResult result = runner.deploy(deployJob(), workspace, parameters);

        Path expectedDestination = tempDir.resolve("deploy-target/apps/app.jar");
        assertEquals(expectedDestination.toAbsolutePath().normalize(), result.destinationPath());
        assertTrue(Files.isRegularFile(expectedDestination));
        assertEquals("deployable jar", Files.readString(expectedDestination));
        assertEquals(Files.size(expectedDestination), result.bytesCopied());
        assertEquals(StorageChecksums.sha256(expectedDestination), result.checksum());
        assertTrue(result.checksumVerified());
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
                UUID.fromString("00000000-0000-0000-0000-000000000601"),
                UUID.fromString("00000000-0000-0000-0000-000000000602"),
                UUID.fromString("00000000-0000-0000-0000-000000000603"),
                UUID.fromString("00000000-0000-0000-0000-000000000604"),
                UUID.fromString("00000000-0000-0000-0000-000000000605"),
                UUID.fromString("00000000-0000-0000-0000-000000000606"),
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
                Map.of(
                        "artifact_uri",
                        ARTIFACT_URI,
                        "target",
                        Map.of("destination_path", "apps/app.jar"),
                        "verify_checksum",
                        true),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-31T10:00:00Z"));
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
