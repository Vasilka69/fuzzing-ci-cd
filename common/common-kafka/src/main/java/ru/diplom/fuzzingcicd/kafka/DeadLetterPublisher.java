package ru.diplom.fuzzingcicd.kafka;

import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.JobError;

public interface DeadLetterPublisher {

    void publish(ExecutorJobEnvelope originalMessage, JobError error);
}
