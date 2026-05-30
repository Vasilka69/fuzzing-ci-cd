package ru.diplom.cicd.executor.core.workspace;

/**
 * Ошибка управления workspace executor-а.
 */
public class WorkspaceException extends RuntimeException {

    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
