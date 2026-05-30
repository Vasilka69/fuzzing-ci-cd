package ru.diplom.cicd.fuzzing;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;

@SpringBootApplication
public class FuzzingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuzzingServiceApplication.class, args);
    }

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }
}
