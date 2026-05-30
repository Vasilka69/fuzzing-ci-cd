package ru.diplom.cicd.executor.core.workspace;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;

/**
 * Файловая реализация workspace runtime-а.
 *
 * <p>Каждая попытка job получает каталог {@code <root>/<jobExecutionId>}. Повторный вызов create для
 * того же {@code jobExecutionId} переиспользует каталог, но не выполняет idempotency marker logic:
 * это отдельный слой из CORE-010.
 */
public final class LocalWorkspaceManager implements WorkspaceManager {

    public static final String CLEANUP_ALWAYS = "always";
    public static final String CLEANUP_NEVER = "never";

    private final Path root;

    public LocalWorkspaceManager(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public WorkspaceHandle create(UUID jobExecutionId, WorkspacePolicy policy) {
        Objects.requireNonNull(jobExecutionId, "jobExecutionId");
        WorkspacePolicy resolvedPolicy = policy == null ? new WorkspacePolicy(CLEANUP_ALWAYS, false) : policy;
        Path workspaceRoot = root.resolve(jobExecutionId.toString()).normalize();

        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException error) {
            throw new WorkspaceException("Не удалось создать workspace: " + workspaceRoot, error);
        }

        return new WorkspaceHandle(jobExecutionId, workspaceRoot, resolvedPolicy);
    }

    @Override
    public Path resolve(WorkspaceHandle workspace, String relativePath) {
        Objects.requireNonNull(workspace, "workspace");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Относительный путь workspace должен быть непустым");
        }

        Path requestedPath = Path.of(relativePath);
        if (requestedPath.isAbsolute()) {
            throw new WorkspaceException("Абсолютный путь в workspace запрещен: " + relativePath);
        }

        Path workspaceRoot = workspace.root().toAbsolutePath().normalize();
        Path resolvedPath = workspaceRoot.resolve(requestedPath).normalize();
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new WorkspaceException("Путь workspace выходит за пределы рабочей директории: " + relativePath);
        }
        return resolvedPath;
    }

    @Override
    public boolean cleanup(WorkspaceHandle workspace, boolean failed) {
        Objects.requireNonNull(workspace, "workspace");
        if (!shouldCleanup(workspace.policy(), failed)) {
            return false;
        }

        deleteRecursively(workspace.root());
        return true;
    }

    private boolean shouldCleanup(WorkspacePolicy policy, boolean failed) {
        if (failed && policy.preserveOnFailure()) {
            return false;
        }

        String cleanup = policy.cleanup();
        if (cleanup == null || cleanup.isBlank()) {
            cleanup = CLEANUP_ALWAYS;
        }

        return switch (cleanup.toLowerCase(Locale.ROOT)) {
            case CLEANUP_ALWAYS -> true;
            case CLEANUP_NEVER -> false;
            default -> throw new WorkspaceException("Неизвестная политика очистки workspace: " + cleanup);
        };
    }

    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) {
                        throw error;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            throw new WorkspaceException("Не удалось очистить workspace: " + path, error);
        }
    }
}
