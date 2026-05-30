package ru.diplom.cicd.executor.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    void masksPasswordTokenAndPrivateKeyValues() {
        String source = """
                password=build-password-123
                token: synthetic-token-value
                private_key='inline-key-value'
                """;

        String redacted = redactor.redact(source);

        assertEquals("""
                password=[REDACTED]
                token: [REDACTED]
                private_key=[REDACTED]
                """, redacted);
        assertFalse(redacted.contains("build-password-123"));
        assertFalse(redacted.contains("synthetic-token-value"));
        assertFalse(redacted.contains("inline-key-value"));
    }

    @Test
    void masksJsonSecretFieldsAndKeepsJsonShape() {
        String source =
                "{\"login\":\"executor\",\"password\":\"p@ss, with spaces\",\"accessToken\":\"abc.def\",\"safe\":\"value\"}";

        String redacted = redactor.redact(source);

        assertEquals(
                "{\"login\":\"executor\",\"password\":\"[REDACTED]\",\"accessToken\":\"[REDACTED]\",\"safe\":\"value\"}",
                redacted);
        assertFalse(redacted.contains("p@ss"));
        assertFalse(redacted.contains("abc.def"));
    }

    @Test
    void masksAuthorizationBearerHeader() {
        String source = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9 next=visible";

        String redacted = redactor.redact(source);

        assertEquals("Authorization: Bearer [REDACTED] next=visible", redacted);
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test
    void masksPemPrivateKeyBlockBody() {
        String source = """
                before
                -----BEGIN OPENSSH PRIVATE KEY-----
                b3BlbnNzaC1rZXktdjEAAAAABG5vbmU=
                very-secret-line
                -----END OPENSSH PRIVATE KEY-----
                after
                """;

        String redacted = redactor.redact(source);

        assertEquals(
                "before%n-----BEGIN OPENSSH PRIVATE KEY-----%n[REDACTED]%n-----END OPENSSH PRIVATE KEY-----%nafter%n"
                        .formatted(),
                redacted);
        assertFalse(redacted.contains("very-secret-line"));
        assertFalse(redacted.contains("b3BlbnNzaC1rZXktdjEAAAAABG5vbmU="));
    }

    @Test
    void leavesSafeTextUnchanged() {
        String source = "token bucket enabled; password policy checked; private key rotation reminder";

        assertEquals(source, redactor.redact(source));
    }

    @Test
    void returnsNullAndEmptyInputAsIs() {
        assertNull(redactor.redact(null));
        assertEquals("", redactor.redact(""));
    }

    @Test
    void rejectsBlankMask() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new SecretRedactor(" "));

        assertEquals("Маска секрета должна быть непустой", error.getMessage());
    }
}
