package ru.diplom.cicd.contracts.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Тип executor job во внешнем контракте Kafka-сообщений.
 */
public enum JobType {
    VCS("vcs"),
    STORAGE("storage"),
    BUILD("build"),
    FUZZING("fuzzing"),
    DEPLOY("deploy"),
    SCRIPT("script");

    private final String wireValue;

    JobType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static JobType fromWireValue(String wireValue) {
        return Arrays.stream(values())
                .filter(value -> value.wireValue.equals(wireValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный тип job: " + wireValue));
    }
}
