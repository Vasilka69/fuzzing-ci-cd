package ru.diplom.cicd.executor.core.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Маскирует распространенные представления секретов перед публикацией stdout/stderr в логи.
 *
 * <p>Сейчас redactor работает по безопасным синтаксическим признакам. В будущем его можно расширить
 * value-aware режимом через {@code SecretResolver}: передавать известные значения секретов попытки и
 * маскировать их даже без ключей {@code token/password/private_key} рядом со значением.
 */
public final class SecretRedactor {

    public static final String DEFAULT_MASK = "[REDACTED]";

    private static final String SECRET_FIELD_NAMES =
            "password|passwd|pwd|token|access[-_]?token|refresh[-_]?token|api[-_]?key|private[-_]?key";

//    Sonar жаловался, поэтому пока заменён
//    private static final Pattern JSON_SECRET_VALUE =
//            Pattern.compile("(?i)(\"(?:" + SECRET_FIELD_NAMES + ")\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*)(\")");

    private static final Pattern JSON_SECRET_VALUE =
        Pattern.compile("(\"(?i:" + SECRET_FIELD_NAMES + ")\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*+)(\")");

    private static final Pattern KEY_VALUE_SECRET =
            Pattern.compile("(?i)(\\b(?:" + SECRET_FIELD_NAMES + ")\\b\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");

    private static final Pattern AUTHORIZATION_BEARER =
            Pattern.compile("(?i)(\\bAuthorization\\s*:\\s*Bearer\\s+)([^\\s,;&]+)");

    private static final Pattern PRIVATE_KEY_BLOCK =
            Pattern.compile("(?s)(-----BEGIN ([A-Z0-9 ]*PRIVATE KEY)-----).*?(-----END \\2-----)");

    private final String mask;

    public SecretRedactor() {
        this(DEFAULT_MASK);
    }

    public SecretRedactor(String mask) {
        if (mask == null || mask.isBlank()) {
            throw new IllegalArgumentException("Маска секрета должна быть непустой");
        }
        this.mask = mask;
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String redacted = redactPrivateKeys(text);
        redacted = replaceGroup(JSON_SECRET_VALUE, redacted, 2);
        redacted = replaceGroup(KEY_VALUE_SECRET, redacted, 2);
        return replaceGroup(AUTHORIZATION_BEARER, redacted, 2);
    }

    private String redactPrivateKeys(String text) {
        Matcher matcher = PRIVATE_KEY_BLOCK.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(matcher.group(1)
                            + System.lineSeparator()
                            + mask
                            + System.lineSeparator()
                            + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceGroup(Pattern pattern, String text, int secretGroup) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = match.substring(0, matcher.start(secretGroup) - matcher.start())
                    + mask
                    + match.substring(matcher.end(secretGroup) - matcher.start());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
