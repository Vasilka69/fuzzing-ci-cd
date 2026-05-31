package ru.diplom.cicd.deploy.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;
import ru.diplom.cicd.executor.core.storage.StorageUris;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

/**
 * MVP file-copy runner: скачивает release artifact из internal storage и копирует его
 * в локальный target root, не выполняя shell-команды и не принимая absolute paths из job params.
 */
public final class FileCopyDeploymentRunner {

    private final StorageClient storageClient;
    private final Path targetRoot;

    public FileCopyDeploymentRunner(StorageClient storageClient, Path targetRoot) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
        this.targetRoot = Objects.requireNonNull(targetRoot, "Не задан deploy target root")
                .toAbsolutePath()
                .normalize();
    }

    public FileCopyDeploymentResult deploy(
            JobMessage job, WorkspaceHandle workspace, FileCopyDeploymentParameters parameters) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(parameters, "parameters");

        Path downloadedArtifact = workspace.root().resolve("deploy-artifact").resolve(sourceFileName(parameters));
        Path destinationPath = resolveDestination(parameters.destinationPath());
        Path downloadedPath = download(parameters.artifactUri(), downloadedArtifact);
        copy(downloadedPath, destinationPath);
        String checksum = checksum(destinationPath);
        verifyChecksum(parameters, downloadedPath, checksum);
        return new FileCopyDeploymentResult(
                parameters,
                downloadedPath,
                destinationPath,
                size(destinationPath),
                checksum,
                parameters.verifyChecksum());
    }

    private String sourceFileName(FileCopyDeploymentParameters parameters) {
        Path fileName =
                Path.of(StorageUris.namespacePath(parameters.artifactUri())).getFileName();
        if (fileName == null) {
            throw ExecutorJobException.validation("artifact_uri должен указывать на файл артефакта");
        }
        return fileName.toString();
    }

    private Path resolveDestination(Path relativeDestinationPath) {
        Path resolvedPath = targetRoot.resolve(relativeDestinationPath).normalize();
        if (!resolvedPath.startsWith(targetRoot)) {
            throw ExecutorJobException.validation("target.destination_path выходит за пределы deploy target root");
        }
        return resolvedPath;
    }

    private Path download(String artifactUri, Path targetPath) {
        try {
            return storageClient
                    .download(new StorageDownloadRequest(artifactUri, targetPath))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw infrastructureError("Deploy file-copy не смог скачать artifact из storage", exception);
        }
    }

    private void copy(Path sourcePath, Path destinationPath) {
        try {
            Files.createDirectories(destinationPath.getParent());
            Files.copy(
                    sourcePath,
                    destinationPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException exception) {
            throw infrastructureError("Deploy file-copy не смог скопировать artifact в target path", exception);
        }
    }

    private String checksum(Path path) {
        try {
            return StorageChecksums.sha256(path);
        } catch (IOException exception) {
            throw infrastructureError("Deploy file-copy не смог посчитать SHA-256 скопированного artifact", exception);
        }
    }

    private void verifyChecksum(FileCopyDeploymentParameters parameters, Path sourcePath, String destinationChecksum) {
        if (!parameters.verifyChecksum()) {
            return;
        }
        String sourceChecksum = checksum(sourcePath);
        if (!sourceChecksum.equals(destinationChecksum)) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "deploy.file-copy.checksum-mismatch",
                    "Deploy file-copy обнаружил несовпадение SHA-256 после копирования artifact",
                    null,
                    Map.of("artifactUri", parameters.artifactUri()),
                    ExecutionStatus.FAILED);
        }
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw infrastructureError("Deploy file-copy не смог определить размер скопированного artifact", exception);
        }
    }

    private ExecutorJobException infrastructureError(String message, Exception exception) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "deploy.file-copy.infrastructure",
                message,
                exception.getMessage(),
                Map.of("exceptionClass", exception.getClass().getName()),
                ExecutionStatus.FAILED);
    }
}
