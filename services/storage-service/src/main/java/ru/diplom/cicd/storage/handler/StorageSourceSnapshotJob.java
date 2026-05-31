package ru.diplom.cicd.storage.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.storage.backend.LocalFilesystemStorageBackend;
import ru.diplom.cicd.storage.backend.StorageChecksumMismatchException;
import ru.diplom.cicd.storage.backend.StorageSaveRequest;

/**
 * MVP job для сохранения source snapshot в локальный backend storage-service.
 */
@Service
public final class StorageSourceSnapshotJob implements ExecutorJob {

    static final String TEMPLATE_PATH = "storage/source-snapshot";
    private static final String OPERATION_SAVE = "save";

    public static final String OPERATION_KEY = "operation";

    private final LocalFilesystemStorageBackend storageBackend;

    public StorageSourceSnapshotJob(LocalFilesystemStorageBackend storageBackend) {
        this.storageBackend = storageBackend;
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        JobMessage job = context.job();
        validateJobRouting(job);
        validateOperation(job);

        StorageSaveParameters parameters = StorageSaveParameters.from(job);
        ArtifactDescriptor artifact = save(parameters);

        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Source snapshot сохранен в локальное хранилище",
                List.of(artifact),
                Map.of("sizeBytes", artifact.sizeBytes()),
                "Local filesystem storage сохранил артефакт: " + artifact.uri(),
                null,
                additionalData(artifact));
    }

    private void validateJobRouting(JobMessage job) {
        if (job.jobType() != JobType.STORAGE) {
            throw ExecutorJobException.validation("Storage-сервис принимает только jobType=storage");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation(
                    "Storage-сервис сейчас поддерживает только templatePath=storage/source-snapshot");
        }
    }

    private void validateOperation(JobMessage job) {
        Object operation = job.params().getOrDefault(OPERATION_KEY, job.inputs().get(OPERATION_KEY));
        if (operation == null) {
            return;
        }
        if (!(operation instanceof String text) || !OPERATION_SAVE.equals(text)) {
            throw ExecutorJobException.validation("storage/source-snapshot поддерживает только operation=save");
        }
    }

    private ArtifactDescriptor save(StorageSaveParameters parameters) {
        try {
            return storageBackend.save(
                parameters.sourcePath(),
                new StorageSaveRequest(
                    parameters.destinationPath(),
                    parameters.artifactType(),
                    parameters.name(),
                    parameters.contentType(),
                    parameters.metadata(),
                    parameters.expectedChecksumSha256()));
        } catch (StorageChecksumMismatchException | IllegalArgumentException exception) {
            throw ExecutorJobException.validation(exception.getMessage());
        } catch (StorageClientException exception) {
            throw new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "storage.local.save-failed",
                "Не удалось сохранить артефакт в локальное хранилище",
                exception.getMessage(),
                Map.of("exceptionClass", exception.getClass().getName()),
                ExecutionStatus.FAILED);
        }
    }

    private Map<String, Object> additionalData(ArtifactDescriptor artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(OPERATION_KEY, OPERATION_SAVE);
        result.put("storageUri", artifact.uri());
        result.put("checksumSha256", artifact.checksumSha256());
        result.put("sizeBytes", artifact.sizeBytes());
        result.put("contentType", artifact.contentType());
        result.put("metadata", artifact.metadata());
        return result;
    }
}
