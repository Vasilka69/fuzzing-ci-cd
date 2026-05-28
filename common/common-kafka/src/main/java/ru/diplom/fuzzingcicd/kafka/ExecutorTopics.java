package ru.diplom.fuzzingcicd.kafka;

import java.util.Locale;

public final class ExecutorTopics {

    public static final String VCS_JOBS = "jobs.vcs";
    public static final String STORAGE_JOBS = "jobs.storage";
    public static final String BUILD_JOBS = "jobs.build";
    public static final String FUZZING_JOBS = "jobs.fuzzing";
    public static final String DEPLOY_JOBS = "jobs.deploy";
    public static final String SCRIPT_JOBS = "jobs.script";
    public static final String JOB_RESULTS = "jobs.results";
    public static final String DEAD_LETTER = "jobs.dead-letter";

    private ExecutorTopics() {
    }

    public static String inputTopicFor(String jobType) {
        String normalizedJobType = jobType == null ? "" : jobType.toLowerCase(Locale.ROOT);
        return switch (normalizedJobType) {
            case "vcs" -> VCS_JOBS;
            case "storage" -> STORAGE_JOBS;
            case "build" -> BUILD_JOBS;
            case "fuzzing" -> FUZZING_JOBS;
            case "deploy" -> DEPLOY_JOBS;
            case "script" -> SCRIPT_JOBS;
            default -> throw new IllegalArgumentException("Unsupported job type: " + jobType);
        };
    }
}
