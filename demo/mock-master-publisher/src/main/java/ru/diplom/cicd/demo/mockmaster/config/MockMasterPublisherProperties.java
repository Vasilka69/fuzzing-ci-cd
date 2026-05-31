package ru.diplom.cicd.demo.mockmaster.config;

import java.time.Duration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cicd.demo.mock-master")
public record MockMasterPublisherProperties(
        String runId, Duration sendTimeout, Duration stageDelay, String repositoryUrl, Topics topics) {

    private static final String DEFAULT_RUN_ID = "demo-pipeline";
    private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STAGE_DELAY = Duration.ZERO;
    private static final String DEFAULT_REPOSITORY_URL = "file:///demo/repositories/demo-app";

    public MockMasterPublisherProperties(String runId, Duration sendTimeout, Topics topics) {
        this(runId, sendTimeout, null, null, topics);
    }

    public String effectiveRunId() {
        return StringUtils.defaultIfBlank(runId, DEFAULT_RUN_ID).trim();
    }

    public Duration effectiveSendTimeout() {
        return sendTimeout == null ? DEFAULT_SEND_TIMEOUT : sendTimeout;
    }

    public Duration effectiveStageDelay() {
        return stageDelay == null ? DEFAULT_STAGE_DELAY : stageDelay;
    }

    public String effectiveRepositoryUrl() {
        return StringUtils.defaultIfBlank(repositoryUrl, DEFAULT_REPOSITORY_URL).trim();
    }

    public Topics effectiveTopics() {
        return topics == null ? Topics.defaults() : topics;
    }

    public record Topics(String vcs, String build, String fuzzing, String deploy, String script) {

        private static Topics defaults() {
            return new Topics("jobs.vcs", "jobs.build", "jobs.fuzzing", "jobs.deploy", "jobs.script");
        }

        public String vcsTopic() {
            return topicOrDefault(vcs, "jobs.vcs");
        }

        public String buildTopic() {
            return topicOrDefault(build, "jobs.build");
        }

        public String fuzzingTopic() {
            return topicOrDefault(fuzzing, "jobs.fuzzing");
        }

        public String deployTopic() {
            return topicOrDefault(deploy, "jobs.deploy");
        }

        public String scriptTopic() {
            return topicOrDefault(script, "jobs.script");
        }

        private static String topicOrDefault(String topic, String defaultTopic) {
            return StringUtils.defaultIfBlank(topic, defaultTopic).trim();
        }
    }
}
