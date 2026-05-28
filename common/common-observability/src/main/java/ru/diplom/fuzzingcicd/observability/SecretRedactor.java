package ru.diplom.fuzzingcicd.observability;

import java.util.Collection;
import java.util.List;

public final class SecretRedactor {

    public static final String REDACTION = "***";

    private final List<String> secrets;

    public SecretRedactor(Collection<String> secrets) {
        this.secrets = secrets == null
                ? List.of()
                : secrets.stream()
                        .filter(secret -> secret != null && !secret.isBlank())
                        .distinct()
                        .toList();
    }

    public String redact(String value) {
        if (value == null || secrets.isEmpty()) {
            return value;
        }
        String redacted = value;
        for (String secret : secrets) {
            redacted = redacted.replace(secret, REDACTION);
        }
        return redacted;
    }
}
