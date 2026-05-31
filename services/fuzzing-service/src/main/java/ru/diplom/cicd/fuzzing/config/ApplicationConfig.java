package ru.diplom.cicd.fuzzing.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessRunner;

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
}
