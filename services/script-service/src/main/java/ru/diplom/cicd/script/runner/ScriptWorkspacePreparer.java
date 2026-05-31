package ru.diplom.cicd.script.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.executor.core.storage.StorageDownloadRequest;

@Component
public final class ScriptWorkspacePreparer {

    private static final String SCRIPT_WORK_DIR = "script";
    private static final String SCRIPT_FILE_NAME = "script.sh";

    private final StorageClient storageClient;

    public ScriptWorkspacePreparer(StorageClient storageClient) {
        this.storageClient = Objects.requireNonNull(storageClient, "storageClient");
    }

    public ScriptWorkspace prepare(ScriptParameters parameters, Path workspaceRoot) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");

        Path root = workspaceRoot
                .toAbsolutePath()
                .normalize()
                .resolve(SCRIPT_WORK_DIR)
                .normalize();
        ensureInside(workspaceRoot.toAbsolutePath().normalize(), root, "script workspace");
        Path workingDirectory = root.resolve(parameters.workingDirectory()).normalize();
        ensureInside(root, workingDirectory, "working_directory");
        Path scriptPath = root.resolve(".script").resolve(SCRIPT_FILE_NAME).normalize();
        ensureInside(root, scriptPath, "script path");

        try {
            Files.createDirectories(workingDirectory);
            Files.createDirectories(scriptPath.getParent());
            prepareScript(parameters, scriptPath);
            List<ScriptInputArtifact> inputs = downloadInputs(parameters, root);
            return new ScriptWorkspace(root, workingDirectory, scriptPath, inputs);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "script.workspace.prepare",
                    "Не удалось подготовить workspace для script job",
                    exception.getMessage(),
                    Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private void prepareScript(ScriptParameters parameters, Path scriptPath) throws IOException {
        if (parameters.script() != null) {
            Files.writeString(scriptPath, parameters.script(), StandardCharsets.UTF_8);
            return;
        }
        download(parameters.scriptArtifactUri(), scriptPath);
    }

    private List<ScriptInputArtifact> downloadInputs(ScriptParameters parameters, Path root) throws IOException {
        List<ScriptInputArtifact> downloaded = new ArrayList<>();
        for (ScriptInputArtifact artifact : parameters.inputArtifacts()) {
            Path targetPath = root.resolve(artifact.path()).normalize();
            ensureInside(root, targetPath, "input_artifacts.path");
            Path targetParent = targetPath.getParent();
            if (targetParent != null) {
                Files.createDirectories(targetParent);
            }
            download(artifact.uri(), targetPath);
            downloaded.add(artifact);
        }
        return List.copyOf(downloaded);
    }

    private void download(String uri, Path targetPath) {
        try {
            storageClient
                    .download(new StorageDownloadRequest(uri, targetPath))
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException exception) {
            throw storageDownloadException(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            throw storageDownloadException(exception);
        }
    }

    private ExecutorJobException storageDownloadException(Throwable cause) {
        return new ExecutorJobException(
                ErrorType.INFRASTRUCTURE_ERROR,
                "script.storage.download-failed",
                "Не удалось скачать artifact для script job",
                cause.getMessage(),
                Map.of("exceptionClass", cause.getClass().getName()),
                ExecutionStatus.FAILED);
    }

    private void ensureInside(Path root, Path path, String label) {
        if (!path.startsWith(root)) {
            throw new ExecutorJobException(
                    ErrorType.SECURITY_ERROR,
                    "script.workspace.escape",
                    label + " выходит за пределы workspace",
                    path.toString(),
                    Map.of("path", path.toString()),
                    ExecutionStatus.FAILED);
        }
    }
}
