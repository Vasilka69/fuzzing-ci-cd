package ru.diplom.cicd.master.service.messaging.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtifactDescriptor(
        @JsonProperty("artifactId") UUID artifactId,
        @JsonProperty("type") String type,
        @JsonProperty("name") String name,
        @JsonProperty("uri") String uri,
        @JsonProperty("sizeBytes") Long sizeBytes,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("metadata") Map<String, Object> metadata
) {
}
