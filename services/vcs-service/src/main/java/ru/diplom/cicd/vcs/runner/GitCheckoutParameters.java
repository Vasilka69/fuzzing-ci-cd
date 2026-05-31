package ru.diplom.cicd.vcs.runner;

import java.util.Locale;
import java.util.Map;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public record GitCheckoutParameters(
        String repositoryUrl, String safeRepositoryUrl, String ref, String refType, int checkoutDepth) {

    private static final int DEFAULT_CHECKOUT_DEPTH = 1;
    private static final int MAX_CHECKOUT_DEPTH = 100;

    public static GitCheckoutParameters from(JobMessage job) {
        Map<String, Object> params = job.params();
        String vcsType = optionalString(params, "vcs_type");
        if (vcsType != null && !"git".equalsIgnoreCase(vcsType)) {
            throw ExecutorJobException.validation("templatePath=vcs/git требует vcs_type=git");
        }

        String repositoryUrl = requiredString(params, "repository_url");
        String ref = optionalString(params, "ref");
        String refType = normalizeRefType(optionalString(params, "ref_type"), ref);
        int checkoutDepth = checkoutDepth(params.get("checkout_depth"));

        if (booleanParam(params.get("submodules"))) {
            throw ExecutorJobException.validation("Git submodules пока не поддерживаются в MVP checkout");
        }
        if ("commit".equals(refType)) {
            throw ExecutorJobException.validation("Shallow checkout по произвольному commit пока не поддерживается");
        }

        return new GitCheckoutParameters(
                repositoryUrl, GitRepositoryUrlRedactor.redact(repositoryUrl), ref, refType, checkoutDepth);
    }

    private static String requiredString(Map<String, Object> params, String key) {
        String value = optionalString(params, key);
        if (value == null) {
            throw ExecutorJobException.validation("Не задан обязательный параметр VCS job: " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw ExecutorJobException.validation("Параметр VCS job должен быть строкой: " + key);
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeRefType(String refType, String ref) {
        if (refType == null) {
            return ref == null ? "default" : "branch";
        }
        String normalized = refType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "default", "branch", "tag", "commit" -> normalized;
            default -> throw ExecutorJobException.validation("Неизвестный ref_type для Git checkout: " + refType);
        };
    }

    private static int checkoutDepth(Object value) {
        if (value == null) {
            return DEFAULT_CHECKOUT_DEPTH;
        }
        int depth;
        switch (value) {
            case Number number -> depth = number.intValue();
            case String text -> {
                try {
                    depth = Integer.parseInt(text);
                } catch (NumberFormatException exception) {
                    throw ExecutorJobException.validation("checkout_depth должен быть целым числом");
                }
            }
            default -> throw ExecutorJobException.validation("checkout_depth должен быть целым числом");
        }
        if (depth < 1 || depth > MAX_CHECKOUT_DEPTH) {
            throw ExecutorJobException.validation("checkout_depth должен быть в диапазоне от 1 до 100");
        }
        return depth;
    }

    private static boolean booleanParam(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        throw ExecutorJobException.validation("submodules должен быть boolean");
    }
}
