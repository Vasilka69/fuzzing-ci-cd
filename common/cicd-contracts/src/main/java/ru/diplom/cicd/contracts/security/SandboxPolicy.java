package ru.diplom.cicd.contracts.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Запрошенная sandbox policy для запуска пользовательского процесса или контейнера.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SandboxPolicy(
        Boolean privileged,
        Boolean hostNetwork,
        Boolean runAsNonRoot,
        Boolean allowPrivilegeEscalation,
        Boolean readOnlyRootFilesystem,
        List<String> capabilitiesAdd,
        List<String> capabilitiesDrop,
        String seccompProfileType,
        String networkPolicy,
        List<String> egressAllowlist,
        List<String> hostPaths,
        Boolean dockerSocketMount,
        Map<String, Object> additionalData) {

    public SandboxPolicy {
        capabilitiesAdd = ContractCollections.immutableList(capabilitiesAdd);
        capabilitiesDrop = ContractCollections.immutableList(capabilitiesDrop);
        egressAllowlist = ContractCollections.immutableList(egressAllowlist);
        hostPaths = ContractCollections.immutableList(hostPaths);
        additionalData = ContractCollections.immutableMap(additionalData);
    }
}
