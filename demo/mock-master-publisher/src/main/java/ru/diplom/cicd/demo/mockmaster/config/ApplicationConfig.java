package ru.diplom.cicd.demo.mockmaster.config;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.diplom.cicd.contracts.job.JobMessage;
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

    @SuppressWarnings("java:S5738")  // не уверен, что будем юзать не deprecated
    @Bean
    KafkaTemplate<String, JobMessage> demoJobKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    @Bean
    CommandLineRunner demoPipelinePublisher(
            MockMasterPublisherProperties properties,
            DemoPipelineFactory demoPipelineFactory,
            KafkaDemoJobPublisher publisher) {
        return args -> publisher.publish(
                demoPipelineFactory.create(properties),
                properties.effectiveSendTimeout(),
                properties.effectiveStageDelay());
    }
}
