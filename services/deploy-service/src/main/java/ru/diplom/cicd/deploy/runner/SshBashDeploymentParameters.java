package ru.diplom.cicd.deploy.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.executor.core.storage.StorageUris;

public record SshBashDeploymentParameters(
        String artifactUri,
        String environment,
        SshBashTarget target,
        Path destinationPath,
        boolean backupExisting,
        List<String> commands,
        String releaseId) {

    public static final String TEMPLATE_PATH = "deploy/ssh-bash";
    public static final String DEPLOYMENT_TYPE_KEY = "deployment_type";
    public static final String ARTIFACT_URI_KEY = "artifact_uri";
    public static final String ENVIRONMENT_KEY = "environment";
    public static final String TARGET_KEY = "target";
    public static final String HOST_KEY = "host";
    public static final String PORT_KEY = "port";
    public static final String USER_KEY = "user";
    public static final String CREDENTIALS_REF_KEY = "credentials_ref";
    public static final String COPY_KEY = "copy";
    public static final String DESTINATION_PATH_KEY = "destination_path";
    public static final String BACKUP_EXISTING_KEY = "backup_existing";
    public static final String COMMANDS_KEY = "commands";
    public static final String RELEASE_ID_KEY = "release_id";

    private static final String DEPLOYMENT_TYPE = "ssh_bash";
    private static final int DEFAULT_PORT = 22;

    public SshBashDeploymentParameters {
        artifactUri = requireStorageUri(artifactUri, ARTIFACT_URI_KEY);
        environment = defaultIfBlank(environment, "testing").trim();
        target = validateTarget(target);
        destinationPath = normalizeRemoteDestinationPath(destinationPath);
        commands = commands == null ? List.of() : List.copyOf(commands);
        releaseId = trimToNull(releaseId);
    }

    public static SshBashDeploymentParameters from(JobMessage job) {
        validateRouting(job);
        Map<String, Object> params = job.params();
        validateDeploymentType(value(params, DEPLOYMENT_TYPE_KEY, "deploymentType"));
        Map<String, Object> target = object(params, TARGET_KEY);
        Map<String, Object> copy = object(params, COPY_KEY);
        return new SshBashDeploymentParameters(
                optionalString(value(params, ARTIFACT_URI_KEY, "artifactUri"), ARTIFACT_URI_KEY),
                optionalString(value(params, ENVIRONMENT_KEY), ENVIRONMENT_KEY),
                new SshBashTarget(
                        optionalString(value(target, HOST_KEY), HOST_KEY),
                        intValue(value(target, PORT_KEY), DEFAULT_PORT, PORT_KEY),
                        optionalString(value(target, USER_KEY), USER_KEY),
                        optionalString(value(target, CREDENTIALS_REF_KEY, "credentialsRef"), CREDENTIALS_REF_KEY)),
                path(value(copy, DESTINATION_PATH_KEY, "destinationPath")),
                booleanValue(value(copy, BACKUP_EXISTING_KEY, "backupExisting"), true, BACKUP_EXISTING_KEY),
                stringList(value(params, COMMANDS_KEY), COMMANDS_KEY),
                optionalString(value(params, RELEASE_ID_KEY, "releaseId"), RELEASE_ID_KEY));
    }

    private static void validateRouting(JobMessage job) {
        if (job.jobType() != JobType.DEPLOY) {
            throw ExecutorJobException.validation("Deploy-сервис принимает только jobType=deploy");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation(
                    "Deploy-сервис сейчас поддерживает templatePath=deploy/ssh-bash для SSH Bash deployment");
        }
    }

    private static void validateDeploymentType(Object value) {
        if (value == null) {
            return;
        }
        String deploymentType = requireString(value, DEPLOYMENT_TYPE_KEY);
        if (!DEPLOYMENT_TYPE.equals(deploymentType)) {
            throw ExecutorJobException.validation("deployment_type не соответствует templatePath=deploy/ssh-bash");
        }
    }

    private static SshBashTarget validateTarget(SshBashTarget target) {
        if (target == null) {
            throw ExecutorJobException.validation("target должен содержать host, port, user и credentials_ref");
        }
        String host = requireSafeRemoteToken(target.host(), "target.host");
        String user = requireSafeRemoteToken(target.user(), "target.user");
        if (target.port() < 1 || target.port() > 65535) {
            throw ExecutorJobException.validation("target.port должен быть в диапазоне 1..65535");
        }
        String credentialsRef = trimToNull(target.credentialsRef());
        return new SshBashTarget(host, target.port(), user, credentialsRef);
    }

    private static String requireSafeRemoteToken(String value, String key) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw ExecutorJobException.validation(key + " должен быть непустой строкой");
        }
        if (normalized.startsWith("-")
                || normalized.contains("@")
                || normalized.chars().anyMatch(SshBashDeploymentParameters::isUnsafeRemoteTokenChar)) {
            throw ExecutorJobException.validation(key + " содержит недопустимые символы для SSH target");
        }
        return normalized;
    }

    private static boolean isUnsafeRemoteTokenChar(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isISOControl(codePoint);
    }

    private static Path normalizeRemoteDestinationPath(Path path) {
        Path normalizedPath = path.normalize();
        if (!normalizedPath.isAbsolute()) {
            throw ExecutorJobException.validation("copy.destination_path для ssh-bash должен быть absolute path");
        }
        if (startsWithParentTraversal(normalizedPath)
                || normalizedPath.toString().chars().anyMatch(SshBashDeploymentParameters::isUnsafeRemotePathChar)) {
            throw ExecutorJobException.validation(
                    "copy.destination_path для ssh-bash не должен содержать '..', whitespace или control characters");
        }
        if (normalizedPath.getFileName() == null) {
            throw ExecutorJobException.validation("copy.destination_path должен указывать на файл назначения");
        }
        return normalizedPath;
    }

    private static boolean startsWithParentTraversal(Path path) {
        for (int index = 0; index < path.getNameCount(); index++) {
            if ("..".equals(path.getName(index).toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnsafeRemotePathChar(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isISOControl(codePoint);
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
            if (isBlank(stringValue)) {
                throw ExecutorJobException.validation(key + " не должен содержать пустые строки");
            }
            result.add(stringValue);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> object(Map<String, Object> params, String key) {
        Object value = value(params, key);
        if (!(value instanceof Map<?, ?> rawObject)) {
            throw ExecutorJobException.validation(key + " должен быть объектом");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        rawObject.forEach((rawKey, objectValue) -> result.put(requireString(rawKey, key), objectValue));
        return Collections.unmodifiableMap(result);
    }

    private static Path path(Object value) {
        return Path.of(requireString(value, DESTINATION_PATH_KEY));
    }

    private static String requireStorageUri(String value, String key) {
        if (isBlank(value)) {
            throw ExecutorJobException.validation(key + " должен быть непустым storage:// URI");
        }
        String normalized = value.trim();
        if (!normalized.startsWith("storage://")) {
            throw ExecutorJobException.validation(key + " должен использовать схему storage://");
        }
        try {
            StorageUris.namespacePath(normalized);
        } catch (StorageClientException exception) {
            throw ExecutorJobException.validation(key + " должен быть корректным storage:// URI");
        }
        return normalized;
    }

    private static int intValue(Object value, int defaultValue, String key) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException exception) {
                throw ExecutorJobException.validation(key + " должен быть целым числом");
            }
        }
        throw ExecutorJobException.validation(key + " должен быть целым числом");
    }

    private static boolean booleanValue(Object value, boolean defaultValue, String key) {
        switch (value) {
            case null -> {
                return defaultValue;
            }
            case Boolean booleanValue -> {
                return booleanValue;
            }
            case String stringValue
            when "true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue) -> {
                return Boolean.parseBoolean(stringValue);
            }
            default -> throw ExecutorJobException.validation(key + " должен быть boolean");
        }
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

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
