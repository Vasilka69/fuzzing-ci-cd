package ru.diplom.cicd.executor.core.process;

/**
 * Infrastructure error process runner-а: процесс не удалось стартовать, дождаться или корректно остановить.
 */
public final class ProcessRunnerException extends RuntimeException {

    public ProcessRunnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
