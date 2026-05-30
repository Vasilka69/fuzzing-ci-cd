package ru.diplom.cicd.executor.core.workspace;

import java.nio.file.Path;
import java.util.UUID;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;

/**
 * Управляет временным workspace executor-а: создает каталог попытки, безопасно строит пути внутри него
 * и применяет policy очистки после выполнения job.
 */
public interface WorkspaceManager {

    WorkspaceHandle create(UUID jobExecutionId, WorkspacePolicy policy);

    Path resolve(WorkspaceHandle workspace, String relativePath);

    boolean cleanup(WorkspaceHandle workspace, boolean failed);
}
