package ru.diplom.cicd.executor.core.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.event.KafkaExecutorEventPublisher;
import ru.diplom.cicd.executor.core.event.KafkaExecutorEventPublisherProperties;
import ru.diplom.cicd.executor.core.idempotency.FileIdempotencyGuard;
import ru.diplom.cicd.executor.core.idempotency.IdempotencyGuard;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.job.ExecutorJobMetrics;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.log.OpenSearchExecutorLogPublisher;
import ru.diplom.cicd.executor.core.log.OpenSearchExecutorLogPublisherProperties;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.executor.core.workspace.WorkspaceManager;

@SuppressWarnings("java:S5738")  // тут тоже deprecated JsonDeserializer, но будто нет разницы особо
@Configuration
@EnableKafka
public class ExecutorRuntimeConfiguration {

    @Bean
    ConsumerFactory<String, JobMessage> executorJobConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:executor-service}") String groupId,
            @Value("${spring.kafka.consumer.client-id:executor-service}") String clientId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "ru.diplom.cicd.contracts.*");
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, JobMessage.class);
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, JobMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, JobMessage> executorJobConsumerFactory,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
        ConcurrentKafkaListenerContainerFactory<String, JobMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(executorJobConsumerFactory);
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean
    KafkaTemplate<String, ExecutorEventMessage> executorEventKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    @Bean
    WorkspaceManager workspaceManager(
            @Value("${cicd.executor.workspace-root:${java.io.tmpdir}/fuzzing-ci-cd/workspaces}") String root) {
        return new LocalWorkspaceManager(Path.of(root));
    }

    @Bean
    IdempotencyGuard idempotencyGuard(
            @Value("${cicd.executor.idempotency-root:${java.io.tmpdir}/fuzzing-ci-cd/idempotency}") String root) {
        return new FileIdempotencyGuard(Path.of(root));
    }

    @Bean
    SecretRedactor secretRedactor() {
        return new SecretRedactor();
    }

    @Bean
    KafkaExecutorEventPublisherProperties kafkaExecutorEventPublisherProperties(
            @Value("${cicd.executor.results-topic:jobs.results}") String topic) {
        return new KafkaExecutorEventPublisherProperties(topic);
    }

    @Bean
    ExecutorEventPublisher executorEventPublisher(
            KafkaTemplate<String, ExecutorEventMessage> executorEventKafkaTemplate,
            KafkaExecutorEventPublisherProperties properties) {
        return new KafkaExecutorEventPublisher(executorEventKafkaTemplate, properties);
    }

    @Bean
    OpenSearchExecutorLogPublisherProperties openSearchExecutorLogPublisherProperties(
            @Value("${cicd.executor.opensearch.endpoint:http://localhost:9200}") URI endpoint,
            @Value("${cicd.executor.opensearch.index:cicd-executor-events}") String index,
            @Value("${spring.application.name:executor-core}") String sourceService,
            @Value("${cicd.executor.opensearch.request-timeout:10s}") Duration requestTimeout,
            @Value("${cicd.executor.opensearch.refresh:true}") boolean refresh) {
        return new OpenSearchExecutorLogPublisherProperties(endpoint, index, sourceService, requestTimeout, refresh);
    }

    @Bean
    ExecutorLogPublisher executorLogPublisher(OpenSearchExecutorLogPublisherProperties properties) {
        return new OpenSearchExecutorLogPublisher(properties);
    }

    @Bean
    ExecutorJobHandler executorJobHandler(
            WorkspaceManager workspaceManager,
            ExecutorEventPublisher eventPublisher,
            ExecutorLogPublisher logPublisher,
            SecretRedactor secretRedactor,
            IdempotencyGuard idempotencyGuard,
            ExecutorJobMetrics metrics,
            @Value("${cicd.executor.worker-id:executor-worker}") String workerId) {
        return new ExecutorJobHandler(
                workspaceManager, eventPublisher, logPublisher, secretRedactor, idempotencyGuard, metrics, workerId);
    }
}
