package ru.diplom.cicd.executor.core.log;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Настройки OpenSearch publisher-а для {@code JOB_LOG} документов executor-а.
 */
public record OpenSearchExecutorLogPublisherProperties(
        URI endpoint, String index, String sourceService, Duration requestTimeout, boolean refresh) {

    public static final URI DEFAULT_ENDPOINT = URI.create("http://localhost:9200");
    public static final String DEFAULT_INDEX = "cicd-executor-events";
    public static final String DEFAULT_SOURCE_SERVICE = "executor-core";
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    public OpenSearchExecutorLogPublisherProperties {
        if (endpoint == null) {
            endpoint = DEFAULT_ENDPOINT;
        }
        if (index == null || index.isBlank()) {
            index = DEFAULT_INDEX;
        }
        if (sourceService == null || sourceService.isBlank()) {
            sourceService = DEFAULT_SOURCE_SERVICE;
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        }
    }

    public OpenSearchExecutorLogPublisherProperties() {
        this(DEFAULT_ENDPOINT, DEFAULT_INDEX, DEFAULT_SOURCE_SERVICE, DEFAULT_REQUEST_TIMEOUT, true);
    }

    URI documentUri(String documentId) {
        String baseEndpoint = endpoint.toString();
        if (baseEndpoint.endsWith("/")) {
            baseEndpoint = baseEndpoint.substring(0, baseEndpoint.length() - 1);
        }

        String uri = baseEndpoint + "/" + encodePathSegment(index) + "/_doc/" + encodePathSegment(documentId);
        if (refresh) {
            uri = uri + "?refresh=true";
        }
        return URI.create(uri);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
