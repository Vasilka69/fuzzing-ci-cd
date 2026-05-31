package ru.diplom.cicd.script.artifact;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageUploadRequest;

@Component
public final class ScriptOutputPublisher {

    public static final String ARTIFACT_TYPE = "script_output";
    public static final String CONTENT_TYPE = "application/octet-stream";

    private final StorageClient storageClient;

    public ScriptOutputPublisher(StorageClient storageClient) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
    }

    public List<ArtifactDescriptor> publish(JobMessage job, Path workingDirectory, List<ScriptOutputArtifact> outputs) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        Path root = workingDirectory.toAbsolutePath().normalize();
        return outputs.stream().map(output -> upload(job, root, output)).toList();
    }

    public String logs(List<ArtifactDescriptor> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "Script expected outputs не найдены для публикации";
        }
        return "Script expected outputs опубликованы: %d файлов".formatted(artifacts.size());
    }

    private ArtifactDescriptor upload(JobMessage job, Path workingDirectory, ScriptOutputArtifact output) {
        Path sourcePath = workingDirectory.resolve(output.relativePath()).normalize();
        if (!sourcePath.startsWith(workingDirectory) || !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            throw ExecutorJobException.validation("expected output не найден в рабочей директории script job");
        }
        try {
            return storageClient
                    .upload(new StorageUploadRequest(
                            sourcePath,
                            destinationPath(job, output),
                            ARTIFACT_TYPE,
                            output.relativePathText(),
                            CONTENT_TYPE,
                            Map.of(
                                    "jobExecutionId",
                                    job.jobExecutionId().toString(),
                                    "pattern",
                                    output.pattern(),
                                    "path",
                                    output.relativePathText())))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageUploadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageUploadException(exception);
        }
    }

    private String destinationPath(JobMessage job, ScriptOutputArtifact output) {
        return "script-outputs/%s/%s".formatted(job.jobExecutionId(), output.relativePathText());
    }

    private ExecutorJobException storageUploadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "script.outputs.upload-failed",
                "Не удалось загрузить expected output script job во внутреннее хранилище",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }
}
