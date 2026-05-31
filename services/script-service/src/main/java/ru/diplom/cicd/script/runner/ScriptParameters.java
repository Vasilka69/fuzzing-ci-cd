package ru.diplom.cicd.script.runner;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

@SuppressWarnings("java:S1192")
public record ScriptParameters(
        String script,
        String scriptArtifactUri,
        Path workingDirectory,
        Map<String, String> environment,
        List<ScriptInputArtifact> inputArtifacts,
        List<String> expectedOutputPatterns,
        String effectiveNetworkPolicy) {

    public static final String TEMPLATE_PATH = "script/bash";
    public static final String SCRIPT_TYPE_KEY = "script_type";
    public static final String SCRIPT_KEY = "script";
    public static final String SCRIPT_ARTIFACT_URI_KEY = "script_artifact_uri";
    public static final String INPUT_ARTIFACTS_KEY = "input_artifacts";
    public static final String ENVIRONMENT_KEY = "environment";
    public static final String WORKING_DIRECTORY_KEY = "working_directory";
    public static final String EXPECTED_OUTPUTS_KEY = "expected_outputs";
    public static final String DEFAULT_NETWORK_POLICY = "none";

    public ScriptParameters {
        if (StringUtils.isBlank(script) == StringUtils.isBlank(scriptArtifactUri)) {
            throw ExecutorJobException.validation("Нужно задать ровно один параметр: script или script_artifact_uri");
        }
        if (StringUtils.isNotBlank(scriptArtifactUri)) {
            requireStorageUri(scriptArtifactUri, SCRIPT_ARTIFACT_URI_KEY);
        }
        workingDirectory = normalizeRelativePath(
                workingDirectory == null ? Path.of(".") : workingDirectory, WORKING_DIRECTORY_KEY);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        inputArtifacts = inputArtifacts == null ? List.of() : List.copyOf(inputArtifacts);
        expectedOutputPatterns = expectedOutputPatterns == null ? List.of() : List.copyOf(expectedOutputPatterns);
        effectiveNetworkPolicy = StringUtils.defaultIfBlank(effectiveNetworkPolicy, DEFAULT_NETWORK_POLICY)
                .trim();
    }

    public static ScriptParameters from(JobMessage job) {
        validateRouting(job);
        validateScriptType(job);
        return new ScriptParameters(
                optionalString(value(job.params(), SCRIPT_KEY), SCRIPT_KEY),
                optionalString(
                        value(job.params(), SCRIPT_ARTIFACT_URI_KEY, "scriptArtifactUri"), SCRIPT_ARTIFACT_URI_KEY),
                workingDirectory(value(job.params(), WORKING_DIRECTORY_KEY, "workingDirectory")),
                environment(value(job.params(), ENVIRONMENT_KEY)),
                inputArtifacts(value(job.params(), INPUT_ARTIFACTS_KEY, "inputArtifacts")),
                expectedOutputPatterns(value(job.params(), EXPECTED_OUTPUTS_KEY, "expectedOutputs")),
                effectiveNetworkPolicy(job.sandboxPolicy()));
    }

    private static void validateRouting(JobMessage job) {
        if (job.jobType() != JobType.SCRIPT) {
            throw ExecutorJobException.validation("Script-сервис принимает только jobType=script");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation("Script-сервис MVP поддерживает только templatePath=script/bash");
        }
    }

    private static void validateScriptType(JobMessage job) {
        Object rawScriptType = value(job.params(), SCRIPT_TYPE_KEY, "scriptType");
        if (rawScriptType == null) {
            return;
        }
        String scriptType = requireString(rawScriptType, SCRIPT_TYPE_KEY);
        if (!"bash".equals(scriptType)) {
            throw ExecutorJobException.validation("script_type должен быть bash для templatePath=script/bash");
        }
    }

    private static Path workingDirectory(Object value) {
        String rawPath = value == null ? "." : requireString(value, WORKING_DIRECTORY_KEY);
        return normalizeRelativePath(path(rawPath, WORKING_DIRECTORY_KEY), WORKING_DIRECTORY_KEY);
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

    private static List<ScriptInputArtifact> inputArtifacts(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawArtifacts)) {
            throw ExecutorJobException.validation("input_artifacts должен быть массивом объектов");
        }
        List<ScriptInputArtifact> result = new ArrayList<>();
        for (Object rawArtifact : rawArtifacts) {
            result.add(inputArtifact(rawArtifact));
        }
        return List.copyOf(result);
    }

    private static ScriptInputArtifact inputArtifact(Object value) {
        if (!(value instanceof Map<?, ?> rawArtifact)) {
            throw ExecutorJobException.validation("input_artifacts должен содержать только объекты");
        }
        String uri = requireStorageUri(
                requireString(value(rawArtifact, "uri", "artifact_uri", "artifactUri"), "input_artifacts.uri"),
                "input_artifacts.uri");
        Object rawPath = value(rawArtifact, "path", "destination_path", "destinationPath");
        Path relativePath = normalizeRelativePath(
                path(requireString(rawPath, "input_artifacts.path"), "input_artifacts.path"), "input_artifacts.path");
        if (".".equals(relativePath.toString()) || relativePath.getFileName() == null) {
            throw ExecutorJobException.validation("input_artifacts.path должен указывать на файл внутри workspace");
        }
        return new ScriptInputArtifact(uri, relativePath);
    }

    private static List<String> expectedOutputPatterns(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawPatterns)) {
            throw ExecutorJobException.validation("expected_outputs должен быть массивом строк");
        }
        List<String> result = new ArrayList<>();
        for (Object rawPattern : rawPatterns) {
            String pattern = requireString(rawPattern, EXPECTED_OUTPUTS_KEY);
            if (StringUtils.isBlank(pattern)) {
                throw ExecutorJobException.validation("expected_outputs не должен содержать пустые glob-паттерны");
            }
            result.add(pattern);
        }
        return List.copyOf(result);
    }

    private static String effectiveNetworkPolicy(SandboxPolicy sandboxPolicy) {
        if (sandboxPolicy == null || StringUtils.isBlank(sandboxPolicy.networkPolicy())) {
            return DEFAULT_NETWORK_POLICY;
        }
        return sandboxPolicy.networkPolicy().trim();
    }

    private static Path normalizeRelativePath(Path path, String key) {
        Objects.requireNonNull(path, key);
        Path normalizedPath = path.normalize();
        if (normalizedPath.isAbsolute()
                || startsWithParentTraversal(normalizedPath)
                || isWindowsAbsolutePath(normalizedPath)) {
            throw ExecutorJobException.validation(key + " должен быть относительным путем внутри workspace");
        }
        return normalizedPath;
    }

    private static Path path(String value, String key) {
        try {
            return Path.of(StringUtils.defaultIfBlank(value, "."));
        } catch (InvalidPathException exception) {
            throw ExecutorJobException.validation(key + " должен быть корректным путем внутри workspace");
        }
    }

    private static boolean startsWithParentTraversal(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private static boolean isWindowsAbsolutePath(Path path) {
        String value = path.toString().replace('\\', '/');
        return value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && value.charAt(2) == '/';
    }

    private static String requireStorageUri(String value, String key) {
        if (StringUtils.isBlank(value)) {
            throw ExecutorJobException.validation(key + " должен быть непустым storage:// URI");
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

    private static Object value(Map<?, ?> params, String primaryKey, String secondaryKey, String tertiaryKey) {
        if (params.containsKey(primaryKey)) {
            return params.get(primaryKey);
        }
        if (params.containsKey(secondaryKey)) {
            return params.get(secondaryKey);
        }
        return params.get(tertiaryKey);
    }

    private static Object value(Map<String, Object> params, String key) {
        return params.get(key);
    }
}
