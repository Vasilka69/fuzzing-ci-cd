package ru.diplom.cicd.vcs.runner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Маскирует userinfo-часть HTTP(S) Git URL перед публикацией repository metadata и Git stdout/stderr.
 */
final class GitRepositoryUrlRedactor {

    private static final String MASKED_USERINFO = "[REDACTED]@";
    private static final Pattern HTTP_URL_USERINFO = Pattern.compile("(?i)(\\bhttps?://)([^\\s/@?#]+@)");

    private GitRepositoryUrlRedactor() {}

    static String redact(String text) {
        if (StringUtils.isBlank(text)) {
            return StringUtils.EMPTY;
        }

        Matcher matcher = HTTP_URL_USERINFO.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + MASKED_USERINFO));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
