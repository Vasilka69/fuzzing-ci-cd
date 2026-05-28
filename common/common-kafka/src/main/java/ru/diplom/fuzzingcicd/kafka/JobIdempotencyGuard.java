package ru.diplom.fuzzingcicd.kafka;

public interface JobIdempotencyGuard {

    boolean alreadyProcessed(String jobExecutionId);

    void markAccepted(String jobExecutionId);
}
