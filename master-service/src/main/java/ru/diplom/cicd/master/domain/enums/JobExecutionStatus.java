package ru.diplom.cicd.master.domain.enums;

public enum JobExecutionStatus {
    QUEUED("queued"),
    RUNNING("running"),
    WAITING_APPROVAL("waiting_approval"),
    SUCCESS("success"),
    FAILED("failed"),
    TIMEOUT("timeout"),
    CANCELING("canceling"),
    CANCELED("canceled"),
    RETRYING("retrying"),
    SKIPPED("skipped");

    private final String value;

    JobExecutionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isFinalStatus() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELED || this == SKIPPED;
    }

    public static JobExecutionStatus fromValue(String value) {
        for (JobExecutionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown job execution status: " + value);
    }
}
