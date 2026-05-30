package ru.diplom.cicd.contracts.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Тип события executor-а во внешнем контракте Kafka/OpenSearch.
 */
public enum EventType {
    JOB_ACCEPTED("JOB_ACCEPTED"),
    JOB_RUNNING("JOB_RUNNING"),
    JOB_PROGRESS("JOB_PROGRESS"),
    JOB_ARTIFACT("JOB_ARTIFACT"),
    JOB_LOG("JOB_LOG"),
    JOB_FINISHED("JOB_FINISHED"),
    JOB_SKIPPED("JOB_SKIPPED"),
    JOB_CANCELED("JOB_CANCELED"),
    JOB_HEARTBEAT("JOB_HEARTBEAT");

    private final String wireValue;

    EventType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static EventType fromWireValue(String wireValue) {
        return Arrays.stream(values())
                .filter(value -> value.wireValue.equals(wireValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный тип события executor-а: " + wireValue));
    }
}
