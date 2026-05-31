package ru.diplom.cicd.build.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public record BuildParameters(
        BuildTool buildTool,
        String sourceSnapshotUri,
        Path workingDirectory,
        String entrypoint,
        List<String> args,
        Map<String, String> environment) {

    public static final String BUILD_TOOL_KEY = "build_tool";
    public static final String SOURCE_SNAPSHOT_URI_KEY = "source_snapshot_uri";
    public static final String WORKING_DIRECTORY_KEY = "working_directory";
    public static final String ENTRYPOINT_KEY = "entrypoint";
    public static final String ARGS_KEY = "args";
    public static final String ENVIRONMENT_KEY = "environment";

    public BuildParameters {
        Objects.requireNonNull(buildTool, "buildTool");
        requireStorageUri(sourceSnapshotUri);
        workingDirectory = normalizeWorkingDirectory(workingDirectory == null ? Path.of(".") : workingDirectory);
        entrypoint = StringUtils.defaultIfBlank(entrypoint, buildTool.defaultEntrypoint());
        args = args == null ? List.of() : List.copyOf(args);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public static BuildParameters from(JobMessage job) {
        validateRouting(job);
        BuildTool tool = BuildTool.fromTemplatePath(job.templatePath());
        Object explicitTool = value(job.params(), BUILD_TOOL_KEY, "buildTool");
        if (explicitTool != null) {
            BuildTool declaredTool = BuildTool.fromWireValue(requireString(explicitTool, BUILD_TOOL_KEY));
            if (declaredTool != tool) {
                throw ExecutorJobException.validation(
                        "build_tool не соответствует templatePath: " + declaredTool.wireValue());
            }
        }

        return new BuildParameters(
                tool,
                sourceSnapshotUri(value(job.params(), SOURCE_SNAPSHOT_URI_KEY, "sourceSnapshotUri")),
                workingDirectory(value(job.params(), WORKING_DIRECTORY_KEY, "workingDirectory")),
                requireOptionalString(value(job.params(), ENTRYPOINT_KEY), ENTRYPOINT_KEY),
                args(value(job.params(), ARGS_KEY)),
                environment(value(job.params(), ENVIRONMENT_KEY)));
    }

    private static void validateRouting(JobMessage job) {
        if (job.jobType() != JobType.BUILD) {
            throw ExecutorJobException.validation("Build-сервис принимает только jobType=build");
        }
    }

    private static Path workingDirectory(Object value) {
        String rawPath = value == null ? "." : requireString(value, WORKING_DIRECTORY_KEY);
        return normalizeWorkingDirectory(Path.of(StringUtils.defaultIfBlank(rawPath, ".")));
    }

    private static String sourceSnapshotUri(Object value) {
        return requireStorageUri(requireString(value, SOURCE_SNAPSHOT_URI_KEY));
    }

    private static String requireStorageUri(String value) {
        if (StringUtils.isBlank(value)) {
            throw ExecutorJobException.validation("source_snapshot_uri должен быть непустым storage:// URI");
        }
        if (!value.startsWith("storage://")) {
            throw ExecutorJobException.validation("source_snapshot_uri должен использовать схему storage://");
        }
        return value;
    }

    private static Path normalizeWorkingDirectory(Path path) {
        Path normalizedPath = path.normalize();
        if (normalizedPath.isAbsolute() || startsWithParentTraversal(normalizedPath)) {
            throw ExecutorJobException.validation("working_directory должен быть относительным путем внутри workspace");
        }
        return normalizedPath;
    }

    private static boolean startsWithParentTraversal(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private static List<String> args(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawArgs)) {
            throw ExecutorJobException.validation("args должен быть массивом строк");
        }
        List<String> result = new ArrayList<>();
        for (Object rawArg : rawArgs) {
            result.add(requireString(rawArg, ARGS_KEY));
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

    private static String requireOptionalString(Object value, String key) {
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
