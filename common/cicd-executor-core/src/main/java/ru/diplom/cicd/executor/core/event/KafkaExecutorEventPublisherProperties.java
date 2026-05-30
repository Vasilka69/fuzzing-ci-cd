package ru.diplom.cicd.executor.core.event;

/**
 * Настройки Kafka publisher-а служебных событий executor-а.
 */
public record KafkaExecutorEventPublisherProperties(String topic) {

    public static final String DEFAULT_TOPIC = "jobs.results";

    public KafkaExecutorEventPublisherProperties {
        if (topic == null || topic.isBlank()) {
            topic = DEFAULT_TOPIC;
        }
    }

    public KafkaExecutorEventPublisherProperties() {
        this(DEFAULT_TOPIC);
    }
}
