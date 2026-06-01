package ru.diplom.cicd.master.domain.enums;

public enum ExecutorEventType {
    JOB_ACCEPTED,
    JOB_RUNNING,
    JOB_PROGRESS,
    JOB_ARTIFACT,
    JOB_LOG,
    JOB_FINISHED,
    JOB_SKIPPED,
    JOB_CANCELED,
    JOB_HEARTBEAT;

    public static ExecutorEventType fromValue(String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return null;
        }
        String normalized = rawEventType.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        for (ExecutorEventType eventType : values()) {
            if (eventType.name().equals(normalized)) {
                return eventType;
            }
        }
        return null;
    }
}
