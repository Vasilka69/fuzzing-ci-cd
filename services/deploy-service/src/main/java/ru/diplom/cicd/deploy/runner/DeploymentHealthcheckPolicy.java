package ru.diplom.cicd.deploy.runner;

import java.util.Map;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

public record DeploymentHealthcheckPolicy(boolean enabled) {

    public static final String HEALTHCHECK_KEY = "healthcheck";
    public static final String ENABLED_KEY = "enabled";

    public static DeploymentHealthcheckPolicy fromParams(Map<String, Object> params) {
        Object value = params.get(HEALTHCHECK_KEY);
        if (value == null) {
            return defaultEnabled();
        }
        if (!(value instanceof Map<?, ?> healthcheck)) {
            throw ExecutorJobException.validation("healthcheck должен быть объектом");
        }
        return new DeploymentHealthcheckPolicy(booleanValue(healthcheck.get(ENABLED_KEY), true, ENABLED_KEY));
    }

    public static DeploymentHealthcheckPolicy defaultEnabled() {
        return new DeploymentHealthcheckPolicy(true);
    }

    public static DeploymentHealthcheckPolicy disabled() {
        return new DeploymentHealthcheckPolicy(false);
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
            default -> throw ExecutorJobException.validation("healthcheck." + key + " должен быть boolean");
        }
    }
}
