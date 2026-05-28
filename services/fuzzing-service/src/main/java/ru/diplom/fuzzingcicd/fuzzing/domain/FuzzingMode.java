package ru.diplom.fuzzingcicd.fuzzing.domain;

import java.util.Arrays;
import java.util.Optional;

public enum FuzzingMode {
    FAKE("fake"),
    REAL_LLM("real_llm");

    private final String code;

    FuzzingMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<FuzzingMode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst();
    }
}
