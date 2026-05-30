package ru.diplom.cicd.executor.core.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;

/**
 * Runtime-дескриптор каталога, выделенного под конкретный {@code jobExecutionId}.
 */
public record WorkspaceHandle(UUID jobExecutionId, Path root, WorkspacePolicy policy) {

    public WorkspaceHandle {
        Objects.requireNonNull(jobExecutionId, "jobExecutionId");
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(policy, "policy");
    }
}
