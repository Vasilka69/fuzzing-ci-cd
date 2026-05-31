package ru.diplom.cicd.deploy.runner;

import java.util.UUID;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

final class DeploymentReleaseIds {

    private static final String GENERATED_PREFIX = "release-";
    private static final int MAX_LENGTH = 128;

    private DeploymentReleaseIds() {}

    static String resolve(Object rawValue, String key, UUID jobExecutionId) {
        if (rawValue == null) {
            return generated(jobExecutionId);
        }
        if (!(rawValue instanceof String stringValue)) {
            throw ExecutorJobException.validation(key + " должен быть строкой");
        }
        String releaseId = stringValue.trim();
        if (releaseId.isEmpty()) {
            throw ExecutorJobException.validation(key + " не должен быть пустым");
        }
        validate(releaseId, key);
        return releaseId;
    }

    static String validateOrNull(String value, String key) {
        if (value == null) {
            return null;
        }
        String releaseId = value.trim();
        if (releaseId.isEmpty()) {
            throw ExecutorJobException.validation(key + " не должен быть пустым");
        }
        validate(releaseId, key);
        return releaseId;
    }

    private static String generated(UUID jobExecutionId) {
        if (jobExecutionId == null) {
            throw ExecutorJobException.validation("jobExecutionId обязателен для генерации release_id");
        }
        return GENERATED_PREFIX + jobExecutionId;
    }

    private static void validate(String releaseId, String key) {
        if (releaseId.length() > MAX_LENGTH) {
            throw ExecutorJobException.validation(key + " не должен быть длиннее 128 символов");
        }
        if (".".equals(releaseId) || "..".equals(releaseId) || releaseId.contains("..")) {
            throw ExecutorJobException.validation(key + " не должен содержать path traversal");
        }
        if (!isAsciiLetterOrDigit(releaseId.charAt(0))) {
            throw ExecutorJobException.validation(key + " должен начинаться с ASCII-буквы или цифры");
        }
        if (releaseId.chars().anyMatch(DeploymentReleaseIds::isUnsafeReleaseIdChar)) {
            throw ExecutorJobException.validation(
                    key + " должен содержать только ASCII-буквы, цифры, '.', '_' или '-'");
        }
    }

    private static boolean isUnsafeReleaseIdChar(int codePoint) {
        return !isAsciiLetterOrDigit(codePoint) && codePoint != '.' && codePoint != '_' && codePoint != '-';
    }

    private static boolean isAsciiLetterOrDigit(int codePoint) {
        return codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= 'A' && codePoint <= 'Z'
                || codePoint >= '0' && codePoint <= '9';
    }
}
