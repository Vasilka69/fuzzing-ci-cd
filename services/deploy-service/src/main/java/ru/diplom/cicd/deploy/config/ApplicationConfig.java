package ru.diplom.cicd.deploy.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;

@Configuration
public class ApplicationConfig {

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }

    @Bean
    StorageClient storageClient() {
        return new LocalStorageClient(Path.of(System.getProperty("java.io.tmpdir"), "fuzzing-ci-cd", "storage"));
    }

    @Bean
    FileCopyDeploymentRunner fileCopyDeploymentRunner(StorageClient storageClient) {
        // TODO: заменить локальный root на resolver target.connection_ref, когда появится target registry.
        Path targetRoot = Path.of(System.getProperty("java.io.tmpdir"), "fuzzing-ci-cd", "deploy-targets");
        return new FileCopyDeploymentRunner(storageClient, targetRoot);
    }
}
