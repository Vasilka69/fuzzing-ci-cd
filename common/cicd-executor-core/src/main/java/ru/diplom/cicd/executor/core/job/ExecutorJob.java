package ru.diplom.cicd.executor.core.job;

/**
 * Сервисная реализация одного executor job внутри общего runtime pipeline.
 */
@FunctionalInterface
public interface ExecutorJob {

    @SuppressWarnings("java:S112")
    ExecutorJobResult execute(ExecutorJobContext context) throws Exception;
}
