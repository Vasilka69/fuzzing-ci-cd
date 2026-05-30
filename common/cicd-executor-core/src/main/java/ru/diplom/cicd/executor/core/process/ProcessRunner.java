package ru.diplom.cicd.executor.core.process;

/**
 * Запускает контролируемый дочерний процесс executor-а.
 */
public interface ProcessRunner {

    ProcessExecutionResult run(ProcessExecutionRequest request);
}
