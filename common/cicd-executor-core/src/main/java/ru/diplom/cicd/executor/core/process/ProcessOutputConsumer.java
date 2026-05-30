package ru.diplom.cicd.executor.core.process;

/**
 * Получает stdout/stderr chunk-и сразу после чтения из дочернего процесса.
 */
@FunctionalInterface
public interface ProcessOutputConsumer {

    ProcessOutputConsumer NOOP = chunk -> {};

    void accept(ProcessOutputChunk chunk);
}
