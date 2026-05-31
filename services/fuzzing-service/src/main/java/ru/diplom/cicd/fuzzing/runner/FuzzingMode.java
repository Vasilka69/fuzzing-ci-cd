package ru.diplom.cicd.fuzzing.runner;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public enum FuzzingMode {
    FAKE("fake"),
    REAL("real");

    private final String wireValue;

    FuzzingMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static FuzzingMode fromWireValue(String value) {
        String normalized = StringUtils.defaultIfBlank(value, FAKE.wireValue);
        return Arrays.stream(values())
                .filter(mode -> mode.wireValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> ExecutorJobException.validation("Неподдерживаемый fuzzing mode: " + value));
    }
}
