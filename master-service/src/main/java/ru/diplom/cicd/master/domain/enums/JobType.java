package ru.diplom.cicd.master.domain.enums;

public enum JobType {
    VCS("vcs"),
    STORAGE("storage"),
    BUILD("build"),
    FUZZING("fuzzing"),
    DEPLOY("deploy"),
    SCRIPT("script");

    private final String value;

    JobType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static JobType fromValue(String value) {
        for (JobType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown job type: " + value);
    }
}
