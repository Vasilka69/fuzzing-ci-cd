package ru.diplom.fuzzingcicd.kafka;

import ru.diplom.fuzzingcicd.contracts.executor.ExecutorResultEvent;

public interface ExecutorResultPublisher {

    void publish(ExecutorResultEvent resultEvent);
}
