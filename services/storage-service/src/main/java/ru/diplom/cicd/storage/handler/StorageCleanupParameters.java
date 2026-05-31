package ru.diplom.cicd.storage.handler;

import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.executor.core.storage.StorageUris;

record StorageCleanupParameters(String namespacePath, boolean recursive) {

    private static final List<String> TARGET_KEYS =
            List.of("storageUri", "storage_uri", "namespacePath", "namespace_path", "targetPath", "target_path");

    static StorageCleanupParameters from(JobMessage job) {
        String target = requiredString(job, TARGET_KEYS, "Не задан storageUri/namespacePath для storage cleanup job");
        String namespacePath = namespacePath(target);
        boolean recursive = optionalValue(job.params(), job.inputs(), "recursive")
                .map(StorageCleanupParameters::booleanValue)
                .orElse(false);
        return new StorageCleanupParameters(namespacePath, recursive);
    }

    private static String namespacePath(String target) {
        try {
            if (target.startsWith("storage://")) {
                return StorageUris.namespacePath(target);
            }
            return StorageUris.normalizeNamespacePath(target);
        } catch (StorageClientException | IllegalArgumentException exception) {
            throw ExecutorJobException.validation(exception.getMessage());
        }
    }

    private static String requiredString(JobMessage job, List<String> keys, String message) {
        return keys.stream()
                .map(key -> optionalValue(job.params(), job.inputs(), key))
                .flatMap(java.util.Optional::stream)
                .map(value -> stringValue(value, "Параметр storage cleanup job должен быть непустой строкой"))
                .findFirst()
                .orElseThrow(() -> ExecutorJobException.validation(message));
    }

    private static java.util.Optional<Object> optionalValue(
            Map<String, Object> primary, Map<String, Object> secondary, String key) {
        if (primary.containsKey(key)) {
            return java.util.Optional.ofNullable(primary.get(key));
        }
        return java.util.Optional.ofNullable(secondary.get(key));
    }

    private static String stringValue(Object value, String message) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw ExecutorJobException.validation(message);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.parseBoolean(text);
        }
        throw ExecutorJobException.validation("Параметр recursive storage cleanup job должен быть boolean");
    }
}
