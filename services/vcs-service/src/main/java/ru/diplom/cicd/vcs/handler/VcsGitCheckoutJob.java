package ru.diplom.cicd.vcs.handler;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;
import ru.diplom.cicd.vcs.runner.GitCheckoutParameters;
import ru.diplom.cicd.vcs.runner.GitCheckoutResult;
import ru.diplom.cicd.vcs.runner.GitCheckoutRunner;
import ru.diplom.cicd.vcs.snapshot.SourceSnapshotArchive;
import ru.diplom.cicd.vcs.snapshot.SourceSnapshotArchiver;

/**
 * Выполняет MVP-сценарий {@code vcs/git}: shallow checkout и фиксация фактического commit hash.
 */
@Service
public final class VcsGitCheckoutJob implements ExecutorJob {

    static final String TEMPLATE_PATH = "vcs/git";
    private static final String CHECKOUT_DIRECTORY = "source";
    private static final String SNAPSHOT_FILE_NAME = "source-snapshot.tar.gz";
    private static final String SNAPSHOT_ARTIFACT_TYPE = "source_snapshot";
    private static final String SNAPSHOT_CONTENT_TYPE = "application/gzip";

    public static final String CHECKOUT_DEPTH_KEY = "checkoutDepth";
    public static final String VCS_TYPE_KEY = "vcsType";
    public static final String COMMIT_HASH_KEY = "commitHash";

    private final GitCheckoutRunner gitCheckoutRunner;
    private final SourceSnapshotArchiver sourceSnapshotArchiver;
    private final StorageClient storageClient;

    public VcsGitCheckoutJob(
            GitCheckoutRunner gitCheckoutRunner,
            SourceSnapshotArchiver sourceSnapshotArchiver,
            StorageClient storageClient) {
        this.gitCheckoutRunner = Objects.requireNonNull(gitCheckoutRunner, "gitCheckoutRunner");
        this.sourceSnapshotArchiver = Objects.requireNonNull(sourceSnapshotArchiver, "sourceSnapshotArchiver");
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        JobMessage job = context.job();
        validateJobRouting(job);

        GitCheckoutParameters parameters = GitCheckoutParameters.from(job);
        Path checkoutPath =
                context.workspace().root().resolve(CHECKOUT_DIRECTORY).normalize();
        GitCheckoutResult checkout = gitCheckoutRunner.checkout(parameters, checkoutPath, job.timeoutSeconds());
        SourceSnapshotArchive snapshot = sourceSnapshotArchiver.create(
                checkoutPath,
                context.workspace().root().resolve(SNAPSHOT_FILE_NAME),
                context.workspace().root(),
                job.timeoutSeconds());
        ArtifactDescriptor snapshotArtifact = uploadSnapshot(job, parameters, checkout, snapshot);

        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Git checkout, архивация и upload source snapshot завершены успешно",
                List.of(snapshotArtifact),
                Map.of(CHECKOUT_DEPTH_KEY, parameters.checkoutDepth(), "snapshotSizeBytes", snapshot.sizeBytes()),
                logs(checkout, snapshot),
                null,
                additionalData(parameters, checkout, snapshot, snapshotArtifact));
    }

    private void validateJobRouting(JobMessage job) {
        if (job.jobType() != JobType.VCS) {
            throw ExecutorJobException.validation("VCS-сервис принимает только jobType=vcs");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation("VCS-сервис сейчас поддерживает только templatePath=vcs/git");
        }
    }

    private Map<String, Object> additionalData(
            GitCheckoutParameters parameters,
            GitCheckoutResult checkout,
            SourceSnapshotArchive snapshot,
            ArtifactDescriptor snapshotArtifact) {
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put(VCS_TYPE_KEY, "git");
        repository.put("repositoryUrl", parameters.safeRepositoryUrl());
        if (parameters.ref() != null) {
            repository.put("ref", parameters.ref());
        }
        repository.put("refType", parameters.refType());
        repository.put(CHECKOUT_DEPTH_KEY, parameters.checkoutDepth());
        repository.put("submodules", false);

        Map<String, Object> checkoutData = new LinkedHashMap<>();
        checkoutData.put(COMMIT_HASH_KEY, checkout.commitHash());
        checkoutData.put("relativePath", CHECKOUT_DIRECTORY);

        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("format", snapshot.format());
        snapshotData.put("fileName", snapshot.fileName());
        snapshotData.put("relativePath", snapshot.relativePath());
        snapshotData.put("sizeBytes", snapshot.sizeBytes());
        snapshotData.put("checksumSha256", snapshot.checksumSha256());
        snapshotData.put("uri", snapshotArtifact.uri());
        snapshotData.put("artifactId", snapshotArtifact.artifactId());
        snapshotData.put("contentType", snapshotArtifact.contentType());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(VCS_TYPE_KEY, "git");
        result.put(COMMIT_HASH_KEY, checkout.commitHash());
        result.put("sourceSnapshotUri", snapshotArtifact.uri());
        result.put("repository", repository);
        result.put("checkout", checkoutData);
        result.put("snapshot", snapshotData);
        return Map.copyOf(result);
    }

    private ArtifactDescriptor uploadSnapshot(
            JobMessage job,
            GitCheckoutParameters parameters,
            GitCheckoutResult checkout,
            SourceSnapshotArchive snapshot) {
        try {
            return storageClient
                    .upload(new StorageUploadRequest(
                            snapshot.path(),
                            snapshotDestinationPath(job),
                            SNAPSHOT_ARTIFACT_TYPE,
                            SNAPSHOT_FILE_NAME,
                            SNAPSHOT_CONTENT_TYPE,
                            snapshotMetadata(job, parameters, checkout, snapshot)))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageUploadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageUploadException(exception);
        }
    }

    private ExecutorJobException storageUploadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "vcs.snapshot.upload-failed",
                "Не удалось загрузить source snapshot во внутреннее хранилище",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private String snapshotDestinationPath(JobMessage job) {
        return "source-snapshots/%s/%s".formatted(job.jobExecutionId(), SNAPSHOT_FILE_NAME);
    }

    private Map<String, Object> snapshotMetadata(
            JobMessage job,
            GitCheckoutParameters parameters,
            GitCheckoutResult checkout,
            SourceSnapshotArchive snapshot) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("jobExecutionId", job.jobExecutionId());
        metadata.put("pipelineRunId", job.pipelineRunId());
        metadata.put("pipelineId", job.pipelineId());
        metadata.put("stageId", job.stageId());
        metadata.put("jobId", job.jobId());
        metadata.put("jobType", job.jobType().wireValue());
        metadata.put("templatePath", job.templatePath());
        metadata.put(VCS_TYPE_KEY, "git");
        metadata.put("refType", parameters.refType());
        metadata.put(CHECKOUT_DEPTH_KEY, parameters.checkoutDepth());
        metadata.put(COMMIT_HASH_KEY, checkout.commitHash());
        metadata.put("format", snapshot.format());
        metadata.put("checksumSha256", snapshot.checksumSha256());
        metadata.put("sizeBytes", snapshot.sizeBytes());
        return Collections.unmodifiableMap(metadata);
    }

    private String logs(GitCheckoutResult checkout, SourceSnapshotArchive snapshot) {
        return checkout.logs() + System.lineSeparator() + snapshot.logs();
    }
}
