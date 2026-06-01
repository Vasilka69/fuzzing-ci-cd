package ru.diplom.cicd.master.domain.enums;

public enum StageRunPolicy {
    SEQUENTIAL("sequential"),
    PARALLEL("parallel");

    private final String value;

    StageRunPolicy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
