package ru.diplom.cicd.demo.mockmaster.config;

import java.time.Clock;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.diplom.cicd.demo.mockmaster.pipeline.DemoPipelineFactory;
import ru.diplom.cicd.demo.mockmaster.publisher.KafkaDemoJobPublisher;

@Configuration
@EnableConfigurationProperties(MockMasterPublisherProperties.class)
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DemoPipelineFactory demoPipelineFactory(Clock clock) {
        return new DemoPipelineFactory(clock);
    }

    @Bean
    CommandLineRunner demoPipelinePublisher(
            MockMasterPublisherProperties properties,
            DemoPipelineFactory demoPipelineFactory,
            KafkaDemoJobPublisher publisher) {
        return args -> publisher.publish(demoPipelineFactory.create(properties), properties.effectiveSendTimeout());
    }
}
