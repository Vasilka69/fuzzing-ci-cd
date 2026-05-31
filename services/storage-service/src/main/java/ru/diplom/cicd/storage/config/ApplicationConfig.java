package ru.diplom.cicd.storage.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.executor.core.config.ExecutorRuntimeConfiguration;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.job.KafkaExecutorJobConsumer;
import ru.diplom.cicd.storage.handler.StorageCleanupJob;
import ru.diplom.cicd.storage.handler.StorageSourceSnapshotJob;

@Configuration
@Import(ExecutorRuntimeConfiguration.class)
public class ApplicationConfig {

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }

    @Bean
    KafkaExecutorJobConsumer kafkaExecutorJobConsumer(
            ExecutorJobHandler handler, StorageSourceSnapshotJob sourceSnapshotJob, StorageCleanupJob cleanupJob) {
        return new KafkaExecutorJobConsumer(
                handler, Map.of("storage/source-snapshot", sourceSnapshotJob, "storage/cleanup", cleanupJob));
    }
}
