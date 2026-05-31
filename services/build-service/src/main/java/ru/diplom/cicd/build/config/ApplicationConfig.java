package ru.diplom.cicd.build.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.build.handler.BuildJob;
import ru.diplom.cicd.executor.core.config.ExecutorRuntimeConfiguration;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.job.KafkaExecutorJobConsumer;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;

@Configuration
@Import(ExecutorRuntimeConfiguration.class)
public class ApplicationConfig {

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }

    @Bean
    ProcessRunner processRunner() {
        return new LocalProcessRunner();
    }

    @Bean
    StorageClient storageClient(
            @Value("${cicd.executor.storage-root:${java.io.tmpdir}/fuzzing-ci-cd/storage}") String root) {
        return new LocalStorageClient(Path.of(root));
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    KafkaExecutorJobConsumer kafkaExecutorJobConsumer(ExecutorJobHandler handler, BuildJob job) {
        return new KafkaExecutorJobConsumer(handler, Map.of("build/maven", job, "build/gradle", job));
    }
}
