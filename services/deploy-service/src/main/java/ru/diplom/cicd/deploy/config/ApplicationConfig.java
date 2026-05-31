package ru.diplom.cicd.deploy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.deploy.handler.DeployJob;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentParameters;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentRunner;
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
    StorageClient storageClient(
            @Value("${cicd.executor.storage-root:${java.io.tmpdir}/fuzzing-ci-cd/storage}") String root) {
        return new LocalStorageClient(Path.of(root));
    }

    @Bean
    ProcessRunner processRunner() {
        return new LocalProcessRunner();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    FileCopyDeploymentRunner fileCopyDeploymentRunner(
            StorageClient storageClient,
            @Value("${cicd.deploy.file-copy.target-root:${java.io.tmpdir}/fuzzing-ci-cd/deploy-targets}")
                    String targetRoot) {
        // TODO: заменить локальный root на resolver target.connection_ref, когда появится target registry.
        return new FileCopyDeploymentRunner(storageClient, Path.of(targetRoot));
    }

    @Bean
    SshBashDeploymentRunner sshBashDeploymentRunner(StorageClient storageClient, ProcessRunner processRunner) {
        return new SshBashDeploymentRunner(storageClient, processRunner);
    }

    @Bean
    KafkaExecutorJobConsumer kafkaExecutorJobConsumer(ExecutorJobHandler handler, DeployJob job) {
        return new KafkaExecutorJobConsumer(
                handler,
                Map.of(
                        FileCopyDeploymentParameters.TEMPLATE_PATH,
                        job,
                        SshBashDeploymentParameters.TEMPLATE_PATH,
                        job));
    }
}
