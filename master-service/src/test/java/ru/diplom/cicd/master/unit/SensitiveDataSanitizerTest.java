package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

class SensitiveDataSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SensitiveDataSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new SensitiveDataSanitizer(objectMapper);
    }

    @Test
    void redact_masksSensitiveValuesAndKeepsReferences() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "token":"abc",
                  "secretRefId":"ref-1",
                  "nested":{
                    "password":"123",
                    "tokenRef":"token-ref",
                    "credentials":{"api_key":"k"}
                  },
                  "arr":[{"client_secret":"secret"}, {"value":"ok"}]
                }
                """);

        var redacted = sanitizer.redact(node);

        assertEquals(SensitiveDataSanitizer.REDACTED_VALUE, redacted.path("token").asText());
        assertEquals("ref-1", redacted.path("secretRefId").asText());
        assertEquals(SensitiveDataSanitizer.REDACTED_VALUE, redacted.path("nested").path("password").asText());
        assertEquals("token-ref", redacted.path("nested").path("tokenRef").asText());
        assertEquals(SensitiveDataSanitizer.REDACTED_VALUE, redacted.path("nested").path("credentials").asText());
        assertEquals(SensitiveDataSanitizer.REDACTED_VALUE, redacted.path("arr").path(0).path("client_secret").asText());
        assertEquals("ok", redacted.path("arr").path(1).path("value").asText());
    }

    @Test
    void requireNoInlineSecrets_throwsForSensitiveKeys() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "password":"p",
                  "nested":{"api_key":"k"},
                  "tokenRef":"safe"
                }
                """);

        ApiException ex = assertThrows(ApiException.class, () -> sanitizer.requireNoInlineSecrets(node, "params"));
        assertEquals("inline_secret_not_allowed", ex.getCode());

        var details = assertInstanceOf(SensitiveDataSanitizer.InlineSecretDetails.class, ex.getDetails());
        assertEquals("params", details.field());
        assertTrue(details.paths().contains("$.password"));
        assertTrue(details.paths().contains("$.nested.api_key"));
    }

    @Test
    void requireNoInlineSecrets_allowsReferenceFields() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "secret_ref":"sec/demo",
                  "tokenRef":"token/demo",
                  "credentialsRef":"cred/demo"
                }
                """);

        assertDoesNotThrow(() -> sanitizer.requireNoInlineSecrets(node, "params"));
    }
}
