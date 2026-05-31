package ru.diplom.cicd.deploy.runner;

import java.util.LinkedHashMap;
import java.util.Map;

public record DeploymentHealthcheckResult(
        boolean enabled, String type, String status, boolean passed, long durationMs, String details) {

    public static DeploymentHealthcheckResult skipped(String type) {
        return new DeploymentHealthcheckResult(
                false, type, "SKIPPED", true, 0, "Healthcheck отключен в параметрах job");
    }

    public static DeploymentHealthcheckResult success(String type, long durationMs, String details) {
        return new DeploymentHealthcheckResult(true, type, "SUCCESS", true, durationMs, details);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("type", type);
        data.put("status", status);
        data.put("passed", passed);
        data.put("durationMs", durationMs);
        data.put("details", details);
        return Map.copyOf(data);
    }
}
