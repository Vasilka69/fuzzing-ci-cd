package ru.diplom.cicd.executor.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;

/**
 * Публикует {@code JOB_LOG} документы в OpenSearch через HTTP API.
 */
public final class OpenSearchExecutorLogPublisher implements ExecutorLogPublisher {

    private final OpenSearchLogSender logSender;
    private final OpenSearchExecutorLogPublisherProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OpenSearchExecutorLogPublisher() {
        this(HttpClient.newHttpClient(), new OpenSearchExecutorLogPublisherProperties());
    }

    public OpenSearchExecutorLogPublisher(OpenSearchExecutorLogPublisherProperties properties) {
        this(HttpClient.newHttpClient(), properties);
    }

    public OpenSearchExecutorLogPublisher(HttpClient httpClient, OpenSearchExecutorLogPublisherProperties properties) {
        this(
                new HttpClientOpenSearchLogSender(
                        Objects.requireNonNull(httpClient, "Не задан HTTP client для OpenSearch"),
                        requireProperties(properties).requestTimeout()),
                requireProperties(properties),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    OpenSearchExecutorLogPublisher(
            OpenSearchLogSender logSender,
            OpenSearchExecutorLogPublisherProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.logSender = Objects.requireNonNull(logSender, "Не задан sender для OpenSearch logs");
        this.properties = Objects.requireNonNull(properties, "Не заданы настройки OpenSearch logs");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Не задан JSON mapper для OpenSearch logs");
        this.clock = Objects.requireNonNull(clock, "Не заданы часы для OpenSearch logs");
    }

    @Override
    public CompletionStage<Void> publish(ExecutorEventMessage logEvent) {
        Objects.requireNonNull(logEvent, "Не задано событие executor log");
        UUID jobExecutionId = require(logEvent.jobExecutionId(), "jobExecutionId");
        UUID messageId = require(logEvent.messageId(), "messageId");
        if (logEvent.eventType() != EventType.JOB_LOG) {
            throw new IllegalArgumentException("ExecutorLogPublisher принимает только события JOB_LOG");
        }
        if (logEvent.logs() == null || logEvent.logs().isBlank()) {
            throw new IllegalArgumentException("Лог-документ JOB_LOG должен содержать непустое поле logs");
        }

        String documentId = jobExecutionId + "-" + messageId;
        String body = serialize(
                OpenSearchLogDocument.from(logEvent, documentId, properties.sourceService(), clock.instant()));

        return logSender.send(properties.documentUri(documentId), body);
    }

    private String serialize(OpenSearchLogDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать executor log для OpenSearch", exception);
        }
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new NullPointerException("Не задано обязательное поле logEvent." + fieldName);
        }
        return value;
    }

    private static OpenSearchExecutorLogPublisherProperties requireProperties(
            OpenSearchExecutorLogPublisherProperties properties) {
        return Objects.requireNonNull(properties, "Не заданы настройки OpenSearch logs");
    }

    @FunctionalInterface
    interface OpenSearchLogSender {
        CompletableFuture<Void> send(URI uri, String body);
    }

    private static final class HttpClientOpenSearchLogSender implements OpenSearchLogSender {

        private final HttpClient httpClient;
        private final Duration requestTimeout;

        private HttpClientOpenSearchLogSender(HttpClient httpClient, Duration requestTimeout) {
            this.httpClient = httpClient;
            this.requestTimeout = requestTimeout;
        }

        @Override
        public CompletableFuture<Void> send(URI uri, String body) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();
                        if (statusCode < 200 || statusCode >= 300) {
                            throw new CompletionException(new IllegalStateException(
                                    "OpenSearch вернул HTTP " + statusCode + " при публикации executor log"));
                        }
                        return null;
                    });
        }
    }
}
