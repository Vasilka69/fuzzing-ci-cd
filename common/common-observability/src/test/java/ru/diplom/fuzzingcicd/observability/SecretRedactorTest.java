package ru.diplom.fuzzingcicd.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    @Test
    void redactsRegisteredSecrets() {
        SecretRedactor redactor = new SecretRedactor(List.of("token-123", "password"));

        assertEquals("Authorization=*** password=***", redactor.redact("Authorization=token-123 password=password"));
    }

    @Test
    void ignoresBlankSecrets() {
        SecretRedactor redactor = new SecretRedactor(List.of("", " "));

        assertEquals("visible", redactor.redact("visible"));
    }
}
