package ru.diplom.cicd.storage.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
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
import ru.diplom.cicd.storage.backend.StorageCleanupResult;

/**
 * MVP job для явного удаления временных artifacts из локального backend storage-service.
 */
@Service
public final class StorageCleanupJob implements ExecutorJob {

    static final String TEMPLATE_PATH = "storage/cleanup";
    private static final String OPERATION_CLEANUP = "cleanup";

    private final LocalFilesystemStorageBackend storageBackend;

    public StorageCleanupJob(LocalFilesystemStorageBackend storageBackend) {
        this.storageBackend = storageBackend;
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        JobMessage job = context.job();
        validateJobRouting(job);
        validateOperation(job);

        StorageCleanupParameters parameters = StorageCleanupParameters.from(job);
        StorageCleanupResult cleanup = cleanup(parameters);

        String summary = cleanup.deleted()
                ? "Временные artifacts удалены из локального хранилища"
                : "Временные artifacts уже отсутствуют в локальном хранилище";
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                summary,
                List.of(),
                Map.of("deletedCount", cleanup.deletedCount(), "bytesFreed", cleanup.bytesFreed()),
                "Local filesystem storage cleanup обработал namespace: " + cleanup.storageUri(),
                null,
                additionalData(cleanup, parameters));
    }

    private void validateJobRouting(JobMessage job) {
        if (job.jobType() != JobType.STORAGE) {
            throw ExecutorJobException.validation("Storage-сервис принимает только jobType=storage");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation(
                    "Storage cleanup job поддерживает только templatePath=storage/cleanup");
        }
    }

    private void validateOperation(JobMessage job) {
        Object operation = job.params()
                .getOrDefault(
                        StorageSourceSnapshotJob.OPERATION_KEY,
                        job.inputs().get(StorageSourceSnapshotJob.OPERATION_KEY));
        if (operation == null) {
            return;
        }
        if (!(operation instanceof String text) || !OPERATION_CLEANUP.equals(text)) {
            throw ExecutorJobException.validation("storage/cleanup поддерживает только operation=cleanup");
        }
    }

    private StorageCleanupResult cleanup(StorageCleanupParameters parameters) {
        try {
            return storageBackend.cleanup(parameters.namespacePath(), parameters.recursive());
        } catch (IllegalArgumentException exception) {
            throw ExecutorJobException.validation(exception.getMessage());
        } catch (StorageClientException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "storage.local.cleanup-failed",
                    "Не удалось удалить artifacts из локального хранилища",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private Map<String, Object> additionalData(StorageCleanupResult cleanup, StorageCleanupParameters parameters) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(StorageSourceSnapshotJob.OPERATION_KEY, OPERATION_CLEANUP);
        result.put("storageUri", cleanup.storageUri());
        result.put("namespacePath", cleanup.namespacePath());
        result.put("recursive", parameters.recursive());
        result.put("deleted", cleanup.deleted());
        result.put("deletedCount", cleanup.deletedCount());
        result.put("bytesFreed", cleanup.bytesFreed());
        return result;
    }
}
