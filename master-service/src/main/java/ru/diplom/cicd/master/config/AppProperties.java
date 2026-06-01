package ru.diplom.cicd.master.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Messaging messaging = new Messaging();
    private Outbox outbox = new Outbox();
    private Sse sse = new Sse();
    private OpenSearch opensearch = new OpenSearch();
    private Trigger trigger = new Trigger();

    @Getter
    @Setter
    public static class Messaging {
        private String executorEventsTransport = "kafka";
        private String resultsTopic = "jobs.results";
        private String cancelTopic = "jobs.cancel";
        private Map<String, String> jobTopics = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Outbox {
        private boolean publisherEnabled = true;
        private int batchSize = 100;
        private int maxAttempts = 10;
        private Duration pollInterval = Duration.ofSeconds(2);
    }

    @Getter
    @Setter
    public static class Sse {
        private Duration heartbeatInterval = Duration.ofSeconds(15);
        private Duration emitterTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class OpenSearch {
        private String url = "http://localhost:9200";
        private String index = "cicd-executor-events";
        private int logsPageSize = 200;
        private int pollBatchSize = 200;
        private Integer pollInterval = 50000;
        private String cursorConsumerName = "opensearch-event-poller";
    }

    @Getter
    @Setter
    public static class Trigger {
        private Duration schedulerInterval = Duration.ofSeconds(30);
    }
}
