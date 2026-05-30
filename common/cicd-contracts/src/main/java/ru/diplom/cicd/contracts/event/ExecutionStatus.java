package ru.diplom.cicd.contracts.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Статус выполнения job во внешнем executor event contract.
 */
public enum ExecutionStatus {
    QUEUED("QUEUED"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    TIMEOUT("TIMEOUT"),
    CANCELING("CANCELING"),
    CANCELED("CANCELED"),
    RETRYING("RETRYING"),
    SKIPPED("SKIPPED"),
    WAITING_APPROVAL("WAITING_APPROVAL");

    private final String wireValue;

    ExecutionStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ExecutionStatus fromWireValue(String wireValue) {
        return Arrays.stream(values())
                .filter(value -> value.wireValue.equals(wireValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный статус выполнения job: " + wireValue));
    }
}
