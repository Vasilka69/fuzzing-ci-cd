package ru.diplom.cicd.fuzzing.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public record FuzzingParameters(
        FuzzingMode mode,
        long budgetSeconds,
        List<String> kernelCommand,
        Path kernelWorkingDirectory,
        List<String> targetCommand,
        Map<String, String> environment,
        String targetArtifactUri,
        String sourceSnapshotUri,
        String seedCorpusUri,
        String dictionaryUri,
        String promptUri) {

    public static final String TEMPLATE_PATH = "fuzzing/afl-llm";
    public static final String MODE_KEY = "mode";
    public static final String BUDGET_SECONDS_KEY = "budget_seconds";
    public static final String KERNEL_COMMAND_KEY = "kernel_command";
    public static final String KERNEL_WORKING_DIRECTORY_KEY = "kernel_working_directory";
    public static final String TARGET_COMMAND_KEY = "target_command";
    public static final String ENVIRONMENT_KEY = "environment";
    public static final String TARGET_ARTIFACT_URI_KEY = "target_artifact_uri";
    public static final String SOURCE_SNAPSHOT_URI_KEY = "source_snapshot_uri";
    public static final String SEED_CORPUS_URI_KEY = "seed_corpus_uri";
    public static final String DICTIONARY_URI_KEY = "dictionary_uri";
    public static final String PROMPT_URI_KEY = "prompt_uri";

    private static final long DEFAULT_BUDGET_SECONDS = 30;

    public FuzzingParameters {
        mode = mode == null ? FuzzingMode.FAKE : mode;
        if (budgetSeconds < 1) {
            throw ExecutorJobException.validation("budget_seconds должен быть положительным");
        }
        kernelCommand = kernelCommand == null ? List.of() : List.copyOf(kernelCommand);
        if (mode == FuzzingMode.REAL && kernelCommand.isEmpty()) {
            throw ExecutorJobException.validation(
                    "mode=real пока требует явный kernel_command; встроенный real LLM mode будет добавлен в FUZZING-101");
        }
        kernelWorkingDirectory = normalizeRelativePath(
                kernelWorkingDirectory == null ? Path.of(".") : kernelWorkingDirectory,
                "kernel_working_directory должен быть относительным путем внутри fuzzing kernel root");
        targetCommand = targetCommand == null ? List.of() : List.copyOf(targetCommand);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        targetArtifactUri = optionalStorageUri(targetArtifactUri, TARGET_ARTIFACT_URI_KEY);
        sourceSnapshotUri = optionalStorageUri(sourceSnapshotUri, SOURCE_SNAPSHOT_URI_KEY);
        seedCorpusUri = optionalStorageUri(seedCorpusUri, SEED_CORPUS_URI_KEY);
        dictionaryUri = optionalStorageUri(dictionaryUri, DICTIONARY_URI_KEY);
        promptUri = optionalStorageUri(promptUri, PROMPT_URI_KEY);
    }

    public static FuzzingParameters from(JobMessage job) {
        validateRouting(job);
        Map<String, Object> params = job.params();
        long budgetSeconds = budgetSeconds(params, job.timeoutSeconds());
        if (budgetSeconds > job.timeoutSeconds()) {
            throw ExecutorJobException.validation("budget_seconds не должен превышать timeoutSeconds job");
        }
        return new FuzzingParameters(
                FuzzingMode.fromWireValue(optionalString(value(params, MODE_KEY), MODE_KEY)),
                budgetSeconds,
                stringList(value(params, KERNEL_COMMAND_KEY, "kernelCommand"), KERNEL_COMMAND_KEY),
                workingDirectory(value(params, KERNEL_WORKING_DIRECTORY_KEY, "kernelWorkingDirectory")),
                stringList(value(params, TARGET_COMMAND_KEY, "targetCommand"), TARGET_COMMAND_KEY),
                environment(value(params, ENVIRONMENT_KEY)),
                optionalString(value(params, TARGET_ARTIFACT_URI_KEY, "targetArtifactUri"), TARGET_ARTIFACT_URI_KEY),
                optionalString(value(params, SOURCE_SNAPSHOT_URI_KEY, "sourceSnapshotUri"), SOURCE_SNAPSHOT_URI_KEY),
                optionalString(value(params, SEED_CORPUS_URI_KEY, "seedCorpusUri"), SEED_CORPUS_URI_KEY),
                optionalString(value(params, DICTIONARY_URI_KEY, "dictionaryUri"), DICTIONARY_URI_KEY),
                optionalString(value(params, PROMPT_URI_KEY, "promptUri"), PROMPT_URI_KEY));
    }

    private static void validateRouting(JobMessage job) {
        if (job.jobType() != JobType.FUZZING) {
            throw ExecutorJobException.validation("Fuzzing-сервис принимает только jobType=fuzzing");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation(
                    "Fuzzing-сервис сейчас поддерживает только templatePath=fuzzing/afl-llm");
        }
    }

    private static long budgetSeconds(Map<String, Object> params, long timeoutSeconds) {
        Object value = value(params, BUDGET_SECONDS_KEY, "budgetSeconds");
        if (value == null) {
            return Math.min(DEFAULT_BUDGET_SECONDS, timeoutSeconds);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                throw ExecutorJobException.validation("budget_seconds должен быть целым числом");
            }
        }
        throw ExecutorJobException.validation("budget_seconds должен быть целым числом");
    }

    private static Path workingDirectory(Object value) {
        if (value == null) {
            return Path.of(".");
        }
        return Path.of(requireString(value, KERNEL_WORKING_DIRECTORY_KEY));
    }

    private static List<String> stringList(Object value, String key) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawValues)) {
            throw ExecutorJobException.validation(key + " должен быть массивом строк");
        }
        List<String> result = new ArrayList<>();
        for (Object rawValue : rawValues) {
            String stringValue = requireString(rawValue, key);
            if (StringUtils.isBlank(stringValue)) {
                throw ExecutorJobException.validation(key + " не должен содержать пустые строки");
            }
            result.add(stringValue);
        }
        return List.copyOf(result);
    }

    private static Map<String, String> environment(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawEnvironment)) {
            throw ExecutorJobException.validation("environment должен быть объектом со строковыми значениями");
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawEnvironment.forEach((rawName, rawValue) -> {
            String name = requireString(rawName, ENVIRONMENT_KEY);
            if (StringUtils.isBlank(name)) {
                throw ExecutorJobException.validation("Имя переменной environment не может быть пустым");
            }
            result.put(name, requireString(rawValue, ENVIRONMENT_KEY));
        });
        return Map.copyOf(result);
    }

    private static Path normalizeRelativePath(Path path, String message) {
        Path normalized = path.normalize();
        if (normalized.isAbsolute() || startsWithParentTraversal(normalized)) {
            throw ExecutorJobException.validation(message);
        }
        return normalized;
    }

    private static boolean startsWithParentTraversal(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private static String optionalStorageUri(String value, String key) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        if (!value.startsWith("storage://")) {
            throw ExecutorJobException.validation(key + " должен использовать схему storage://");
        }
        return value;
    }

    private static String optionalString(Object value, String key) {
        return value == null ? null : requireString(value, key);
    }

    private static String requireString(Object value, String key) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw ExecutorJobException.validation(key + " должен быть строкой");
    }

    private static Object value(Map<String, Object> params, String primaryKey, String secondaryKey) {
        if (params.containsKey(primaryKey)) {
            return params.get(primaryKey);
        }
        return params.get(secondaryKey);
    }

    private static Object value(Map<String, Object> params, String key) {
        return params.get(key);
    }
}
