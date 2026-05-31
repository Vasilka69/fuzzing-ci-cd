package ru.diplom.cicd.build.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;

@Configuration
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
    StorageClient storageClient() {
        return new LocalStorageClient(Path.of(System.getProperty("java.io.tmpdir"), "fuzzing-ci-cd", "storage"));
    }
}
