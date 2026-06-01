package ru.diplom.cicd.master.service.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.enums.JobType;

@Component
@RequiredArgsConstructor
public class KafkaTopicResolver {

    private final AppProperties appProperties;

    public String resolveJobTopic(JobType jobType) {
        String topic = appProperties.getMessaging().getJobTopics().get(jobType.value());
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("No Kafka topic configured for job type: " + jobType.value());
        }
        return topic;
    }

    public String resolveResultsTopic() {
        return appProperties.getMessaging().getResultsTopic();
    }

    public String resolveCancelTopic() {
        return appProperties.getMessaging().getCancelTopic();
    }
}
