package ru.diplom.cicd.script;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;

@SpringBootApplication
public class ScriptServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScriptServiceApplication.class, args);
    }

    @Bean
    ExecutorJobMetrics executorJobMetrics(MeterRegistry meterRegistry) {
        return new ExecutorJobMetrics(meterRegistry);
    }
}
