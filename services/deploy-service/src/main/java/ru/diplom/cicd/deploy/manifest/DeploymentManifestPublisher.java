package ru.diplom.cicd.deploy.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentResult;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentParameters;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentResult;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

/**
 * Формирует deployment manifest как audit-документ без секретов и публикует его через storage.
 * В Kafka передается только URI и краткая metadata, чтобы не раздувать event payload.
 */
@SuppressWarnings({"java:S119", "java:S1192"})
@Component
public final class DeploymentManifestPublisher {

    public static final String ARTIFACT_TYPE = "deployment_manifest";
    public static final String MANIFEST_FILE_NAME = "deployment-manifest.json";
    public static final String CONTENT_TYPE = "application/json";

    private final StorageClient storageClient;
    private final ObjectMapper objectMapper;

    public DeploymentManifestPublisher(StorageClient storageClient, ObjectMapper objectMapper) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public DeploymentManifestResult publish(
            JobMessage job, WorkspaceHandle workspace, FileCopyDeploymentResult result) {
        FileCopyDeploymentParameters parameters = result.parameters();
        Map<String, Object> manifest = baseManifest(job, "file_copy", parameters.releaseId(), parameters.environment());
        manifest.put("artifactUri", parameters.artifactUri());
        manifest.put("target", fileCopyTarget(parameters, result));
        manifest.put("result", fileCopyResult(result));
        manifest.put("healthcheck", result.healthcheck().metadata());
        return writeAndUpload(job, workspace, manifest);
    }

    public DeploymentManifestResult publish(JobMessage job, WorkspaceHandle workspace, SshBashDeploymentResult result) {
        SshBashDeploymentParameters parameters = result.parameters();
        Map<String, Object> manifest = baseManifest(job, "ssh_bash", parameters.releaseId(), parameters.environment());
        manifest.put("artifactUri", parameters.artifactUri());
        manifest.put("target", sshBashTarget(parameters));
        manifest.put("result", sshBashResult(result));
        manifest.put("healthcheck", result.healthcheck().metadata());
        return writeAndUpload(job, workspace, manifest);
    }

    private Map<String, Object> baseManifest(
            JobMessage job, String deploymentType, String releaseId, String environment) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("artifactType", ARTIFACT_TYPE);
        manifest.put("jobExecutionId", job.jobExecutionId().toString());
        manifest.put("pipelineRunId", job.pipelineRunId().toString());
        manifest.put("pipelineId", job.pipelineId().toString());
        manifest.put("stageId", job.stageId().toString());
        manifest.put("jobId", job.jobId().toString());
        manifest.put("templatePath", job.templatePath());
        manifest.put("attempt", job.attempt());
        manifest.put("deploymentType", deploymentType);
        manifest.put("releaseId", releaseId);
        manifest.put("environment", environment);
        manifest.put("createdAt", timestamp(job.createdAt()));
        manifest.put("healthcheck", null);
        manifest.put("rollback", null);
        return manifest;
    }

    private String timestamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Map<String, Object> fileCopyTarget(
            FileCopyDeploymentParameters parameters, FileCopyDeploymentResult result) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("destinationPath", result.destinationPath().toString());
        target.put("relativeDestinationPath", parameters.destinationPath().toString());
        putIfPresent(target, "connectionRef", parameters.connectionRef());
        return target;
    }

    private Map<String, Object> fileCopyResult(FileCopyDeploymentResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bytesCopied", result.bytesCopied());
        data.put("deployedArtifactChecksum", result.checksum());
        data.put("checksumVerified", result.checksumVerified());
        return data;
    }

    private Map<String, Object> sshBashTarget(SshBashDeploymentParameters parameters) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("host", parameters.target().host());
        target.put("port", parameters.target().port());
        target.put("user", parameters.target().user());
        target.put("destinationPath", parameters.destinationPath().toString());
        target.put("backupExisting", parameters.backupExisting());
        putIfPresent(target, "credentialsRef", parameters.target().credentialsRef());
        return target;
    }

    private Map<String, Object> sshBashResult(SshBashDeploymentResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bytesCopied", result.artifactBytes());
        data.put("deployedArtifactChecksum", result.artifactChecksum());
        // TODO: заменить на true после добавления remote checksum step для ssh-bash target.
        data.put("checksumVerified", false);
        data.put("commandCount", result.commandResults().size());
        data.put("copyExitCode", result.copyResult().exitCode());
        putIfPresent(data, "backupExitCode", backupExitCode(result));
        return data;
    }

    private Integer backupExitCode(SshBashDeploymentResult result) {
        return result.backupResult() == null ? null : result.backupResult().exitCode();
    }

    private DeploymentManifestResult writeAndUpload(
            JobMessage job, WorkspaceHandle workspace, Map<String, Object> manifest) {
        Path workspaceRoot = workspace.root().toAbsolutePath().normalize();
        Path manifestPath = workspaceRoot.resolve(MANIFEST_FILE_NAME).normalize();
        ensureInsideWorkspace(workspaceRoot, manifestPath);
        writeManifest(manifestPath, manifest);
        ArtifactDescriptor descriptor = upload(job, manifestPath, manifest);
        return new DeploymentManifestResult(descriptor, manifestMetadata(descriptor));
    }

    private void writeManifest(Path manifestPath, Map<String, Object> manifest) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "deploy.manifest.write-failed",
                    "Не удалось записать deployment manifest в workspace",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private ArtifactDescriptor upload(JobMessage job, Path manifestPath, Map<String, Object> manifest) {
        try {
            return storageClient
                    .upload(new StorageUploadRequest(
                            manifestPath,
                            "deployment-manifests/%s/%s".formatted(job.jobExecutionId(), MANIFEST_FILE_NAME),
                            ARTIFACT_TYPE,
                            MANIFEST_FILE_NAME,
                            CONTENT_TYPE,
                            uploadMetadata(job, manifest)))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageUploadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageUploadException(exception);
        }
    }

    private Map<String, Object> uploadMetadata(JobMessage job, Map<String, Object> manifest) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("jobExecutionId", job.jobExecutionId().toString());
        metadata.put("deploymentType", manifest.get("deploymentType"));
        metadata.put("releaseId", manifest.get("releaseId"));
        metadata.put("environment", manifest.get("environment"));
        metadata.put("manifestPath", MANIFEST_FILE_NAME);
        return Map.copyOf(metadata);
    }

    private Map<String, Object> manifestMetadata(ArtifactDescriptor descriptor) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deploymentManifestUri", descriptor.uri());
        metadata.put("deploymentManifestArtifactId", descriptor.artifactId().toString());
        metadata.put("deploymentManifestSizeBytes", descriptor.sizeBytes());
        metadata.put("deploymentManifestChecksumSha256", descriptor.checksumSha256());
        return Map.copyOf(metadata);
    }

    private ExecutorJobException storageUploadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "deploy.manifest.upload-failed",
                "Не удалось загрузить deployment manifest во внутреннее хранилище",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private void ensureInsideWorkspace(Path workspaceRoot, Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw new ExecutorJobException(
                    ErrorType.SECURITY_ERROR,
                    "deploy.manifest.workspace-escape",
                    "Deployment manifest выходит за пределы workspace",
                    null,
                    Map.of("path", path.toString()),
                    ExecutionStatus.FAILED);
        }
    }

    private void putIfPresent(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
