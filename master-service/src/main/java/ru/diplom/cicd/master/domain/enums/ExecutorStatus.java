package ru.diplom.cicd.master.domain.enums;

public enum ExecutorStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELING,
    CANCELED,
    RETRYING,
    SKIPPED,
    WAITING_APPROVAL

    ;

    public static ExecutorStatus fromValue(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        String normalized = rawStatus.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        for (ExecutorStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    public JobExecutionStatus toJobExecutionStatus() {
        return JobExecutionStatus.valueOf(name());
    }
}
