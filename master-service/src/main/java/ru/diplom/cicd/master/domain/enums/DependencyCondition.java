package ru.diplom.cicd.master.domain.enums;

public enum DependencyCondition {
    ON_SUCCESS("on_success"),
    ON_FAILURE("on_failure"),
    ALWAYS("always");

    private final String value;

    DependencyCondition(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DependencyCondition fromValue(String value) {
        for (DependencyCondition condition : values()) {
            if (condition.value.equalsIgnoreCase(value)) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown dependency condition: " + value);
    }
}
