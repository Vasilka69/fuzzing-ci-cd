package ru.diplom.cicd.deploy;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;

@SpringBootApplication
public class DeployServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployServiceApplication.class, args);
    }

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }
}
