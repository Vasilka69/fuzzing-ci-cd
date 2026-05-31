package ru.diplom.cicd.build.runner;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public enum BuildTool {
    MAVEN("maven", "build/maven", "mvn"),
    GRADLE("gradle", "build/gradle", "gradle");

    private final String wireValue;
    private final String templatePath;
    private final String defaultEntrypoint;

    BuildTool(String wireValue, String templatePath, String defaultEntrypoint) {
        this.wireValue = wireValue;
        this.templatePath = templatePath;
        this.defaultEntrypoint = defaultEntrypoint;
    }

    public String wireValue() {
        return wireValue;
    }

    public String templatePath() {
        return templatePath;
    }

    public String defaultEntrypoint() {
        return defaultEntrypoint;
    }

    public static BuildTool fromTemplatePath(String templatePath) {
        for (BuildTool tool : values()) {
            if (tool.templatePath.equals(templatePath)) {
                return tool;
            }
        }
        throw ExecutorJobException.validation(
                "Build-сервис сейчас поддерживает только templatePath=build/maven или build/gradle");
    }

    public static BuildTool fromWireValue(String value) {
        String normalizedValue = StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
        for (BuildTool tool : values()) {
            if (tool.wireValue.equals(normalizedValue)) {
                return tool;
            }
        }
        throw ExecutorJobException.validation("Неподдерживаемый build_tool: " + value);
    }
}
