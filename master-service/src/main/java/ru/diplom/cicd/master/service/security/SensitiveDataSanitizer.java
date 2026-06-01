package ru.diplom.cicd.master.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.exception.ApiException;

@Component
@RequiredArgsConstructor
public class SensitiveDataSanitizer {

    public static final String REDACTED_VALUE = "***redacted***";

    private static final Set<String> SENSITIVE_KEY_MARKERS = Set.of(
            "password",
            "passwd",
            "passphrase",
            "secret",
            "token",
            "apikey",
            "privatekey",
            "accesskey",
            "clientsecret",
            "authorization",
            "credential"
    );

    private final ObjectMapper objectMapper;

    public JsonNode redact(Object payload) {
        if (payload == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.valueToTree(payload);
        return redact(node);
    }

    public JsonNode redact(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }
        if (node.isObject()) {
            ObjectNode source = (ObjectNode) node;
            ObjectNode sanitized = objectMapper.createObjectNode();
            source.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveKey(key) && !looksLikeReferenceKey(key)) {
                    sanitized.set(key, TextNode.valueOf(REDACTED_VALUE));
                } else {
                    sanitized.set(key, redact(value));
                }
            });
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode source = (ArrayNode) node;
            ArrayNode sanitized = objectMapper.createArrayNode();
            source.forEach(item -> sanitized.add(redact(item)));
            return sanitized;
        }
        return node.deepCopy();
    }

    public void requireNoInlineSecrets(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return;
        }
        List<String> paths = new ArrayList<>();
        collectInlineSecrets(node, "$", paths);
        if (paths.isEmpty()) {
            return;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "inline_secret_not_allowed",
                "Inline secret values are not allowed. Use secret refs.",
                new InlineSecretDetails(fieldName, paths)
        );
    }

    private void collectInlineSecrets(JsonNode node, String path, List<String> paths) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String childPath = path + "." + key;
                if (isSensitiveKey(key) && !looksLikeReferenceKey(key) && hasMaterialValue(value)) {
                    paths.add(childPath);
                    return;
                }
                collectInlineSecrets(value, childPath, paths);
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectInlineSecrets(node.get(i), path + "[" + i + "]", paths);
            }
        }
    }

    private boolean hasMaterialValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return !node.asText().isBlank();
        }
        if (node.isArray()) {
            return !node.isEmpty();
        }
        if (node.isObject()) {
            return !node.isEmpty();
        }
        return true;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = normalize(key);
        if (normalized.isEmpty()) {
            return false;
        }
        return SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean looksLikeReferenceKey(String key) {
        String normalized = normalize(key);
        return normalized.endsWith("ref")
                || normalized.endsWith("refid")
                || normalized.contains("reference")
                || normalized.equals("refs")
                || normalized.equals("secretrefs");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public record InlineSecretDetails(String field, List<String> paths) {
    }
}
