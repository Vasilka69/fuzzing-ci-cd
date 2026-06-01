package ru.diplom.cicd.master.service.messaging.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutorError(
        @JsonProperty("code") String code,
        @JsonProperty("type") String type,
        @JsonProperty("retryable") Boolean retryable,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details
) {
}
