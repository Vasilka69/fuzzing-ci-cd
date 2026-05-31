package ru.diplom.cicd.deploy.runner;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.executor.core.storage.StorageUris;

public record FileCopyDeploymentParameters(
        String artifactUri,
        String environment,
        Path destinationPath,
        boolean verifyChecksum,
        DeploymentHealthcheckPolicy healthcheck,
        String releaseId,
        String connectionRef) {

    public static final String TEMPLATE_PATH = "deploy/file-copy";
    public static final String DEPLOYMENT_TYPE_KEY = "deployment_type";
    public static final String ARTIFACT_URI_KEY = "artifact_uri";
    public static final String ENVIRONMENT_KEY = "environment";
    public static final String TARGET_KEY = "target";
    public static final String DESTINATION_PATH_KEY = "destination_path";
    public static final String VERIFY_CHECKSUM_KEY = "verify_checksum";
    public static final String RELEASE_ID_KEY = "release_id";
    public static final String CONNECTION_REF_KEY = "connection_ref";

    private static final String DEPLOYMENT_TYPE = "file_copy";

    public FileCopyDeploymentParameters {
        artifactUri = requireStorageUri(artifactUri, ARTIFACT_URI_KEY);
        environment = defaultIfBlank(environment, "testing").trim();
        destinationPath = normalizeDestinationPath(destinationPath);
        healthcheck = healthcheck == null ? DeploymentHealthcheckPolicy.defaultEnabled() : healthcheck;
        releaseId = DeploymentReleaseIds.validateOrNull(releaseId, RELEASE_ID_KEY);
        connectionRef = trimToNull(connectionRef);
    }

    public static FileCopyDeploymentParameters from(JobMessage job) {
        validateRouting(job);
        Map<String, Object> params = job.params();
        validateDeploymentType(value(params, DEPLOYMENT_TYPE_KEY, "deploymentType"));
        Map<String, Object> target = target(params);
        return new FileCopyDeploymentParameters(
                optionalString(value(params, ARTIFACT_URI_KEY, "artifactUri"), ARTIFACT_URI_KEY),
                optionalString(value(params, ENVIRONMENT_KEY), ENVIRONMENT_KEY),
                path(value(target, DESTINATION_PATH_KEY, "destinationPath")),
                booleanValue(value(params, VERIFY_CHECKSUM_KEY, "verifyChecksum"), true, VERIFY_CHECKSUM_KEY),
                DeploymentHealthcheckPolicy.fromParams(params),
                DeploymentReleaseIds.resolve(
                        value(params, RELEASE_ID_KEY, "releaseId"), RELEASE_ID_KEY, job.jobExecutionId()),
                optionalString(value(target, CONNECTION_REF_KEY, "connectionRef"), CONNECTION_REF_KEY));
    }

    private static void validateRouting(JobMessage job) {
        if (job.jobType() != JobType.DEPLOY) {
            throw ExecutorJobException.validation("Deploy-сервис принимает только jobType=deploy");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation(
                    "Deploy-сервис сейчас поддерживает только templatePath=deploy/file-copy");
        }
    }

    private static void validateDeploymentType(Object value) {
        if (value == null) {
            return;
        }
        String deploymentType = requireString(value, DEPLOYMENT_TYPE_KEY);
        if (!DEPLOYMENT_TYPE.equals(deploymentType)) {
            throw ExecutorJobException.validation("deployment_type не соответствует templatePath=deploy/file-copy");
        }
    }

    private static Map<String, Object> target(Map<String, Object> params) {
        Object value = value(params, TARGET_KEY);
        if (!(value instanceof Map<?, ?> rawTarget)) {
            throw ExecutorJobException.validation("target должен быть объектом с destination_path");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        rawTarget.forEach((key, targetValue) -> result.put(requireString(key, TARGET_KEY), targetValue));
        return Collections.unmodifiableMap(result);
    }

    private static Path path(Object value) {
        return Path.of(requireString(value, DESTINATION_PATH_KEY));
    }

    private static Path normalizeDestinationPath(Path path) {
        Path normalizedPath = path.normalize();
        if (normalizedPath.isAbsolute() || startsWithParentTraversal(normalizedPath)) {
            throw ExecutorJobException.validation(
                    "target.destination_path должен быть относительным путем внутри deploy target root");
        }
        if (normalizedPath.getFileName() == null || ".".equals(normalizedPath.toString())) {
            throw ExecutorJobException.validation("target.destination_path должен указывать на файл назначения");
        }
        return normalizedPath;
    }

    private static boolean startsWithParentTraversal(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
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
