package ru.diplom.cicd.contracts.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Ограничения ресурсов job, полученные из master-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceLimits(
        Integer cpuMillis,
        Long memoryBytes,
        Long diskBytes,
        Integer maxProcesses,
        Map<String, Object> additionalLimits) {

    public ResourceLimits {
        additionalLimits = ContractCollections.immutableMap(additionalLimits);
    }

    public static ResourceLimits empty() {
        return new ResourceLimits(null, null, null, null, Map.of());
    }
}
