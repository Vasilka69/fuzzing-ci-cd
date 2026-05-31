package ru.diplom.cicd.vcs.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.executor.core.config.ExecutorRuntimeConfiguration;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.job.KafkaExecutorJobConsumer;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.storage.LocalStorageClient;
import ru.diplom.cicd.executor.core.storage.StorageClient;
import ru.diplom.cicd.vcs.handler.VcsGitCheckoutJob;

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
    KafkaExecutorJobConsumer kafkaExecutorJobConsumer(ExecutorJobHandler handler, VcsGitCheckoutJob job) {
        return new KafkaExecutorJobConsumer(handler, Map.of("vcs/git", job));
    }
}
