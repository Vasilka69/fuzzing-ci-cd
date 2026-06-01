package ru.diplom.cicd.master.domain.enums;

public enum PipelineRunStatus {
    QUEUED("queued"),
    RUNNING("running"),
    WAITING_APPROVAL("waiting_approval"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELING("canceling"),
    CANCELED("canceled"),
    TIMEOUT("timeout");

    private final String value;

    PipelineRunStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isFinalStatus() {
        return this == SUCCESS || this == FAILED || this == CANCELED || this == TIMEOUT;
    }

    public static PipelineRunStatus fromValue(String value) {
        for (PipelineRunStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline run status: " + value);
    }
}
