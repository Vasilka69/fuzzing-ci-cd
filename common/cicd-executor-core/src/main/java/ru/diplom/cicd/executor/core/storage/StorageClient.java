package ru.diplom.cicd.executor.core.storage;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

/**
 * Единая точка доступа executor-ов к internal storage.
 *
 * <p>Большие payload не должны попадать в Kafka: executor сохраняет файл через этот клиент и дальше
 * передает только {@code storage://} URI и metadata.
 */
public interface StorageClient {

    CompletionStage<ArtifactDescriptor> upload(StorageUploadRequest request);

    CompletionStage<Path> download(StorageDownloadRequest request);
}
