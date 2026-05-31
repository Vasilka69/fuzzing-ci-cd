package ru.diplom.cicd.demo.mockmaster.pipeline;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.contracts.job.JobMessage;

public record DemoJobPublication(String topic, JobMessage message) {

    public DemoJobPublication {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("Kafka topic demo job не должен быть пустым");
        }
        Objects.requireNonNull(message, "message");
        topic = topic.trim();
    }

    public String key() {
        return message.jobExecutionId().toString();
    }
}
