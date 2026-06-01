package ru.diplom.cicd.master.opensearch;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OpenSearchApiClient {

    private final RestClient openSearchRestClient;

    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String index, Map<String, Object> body) {
        Object result = openSearchRestClient.post()
                .uri("/{index}/_search", index)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (result == null) {
            return Map.of();
        }
        return (Map<String, Object>) result;
    }
}
