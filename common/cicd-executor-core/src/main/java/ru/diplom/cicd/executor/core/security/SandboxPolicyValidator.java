package ru.diplom.cicd.executor.core.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

/**
 * Проверяет, что job не запрашивает опасные sandbox-настройки до запуска пользовательского кода.
 */
public final class SandboxPolicyValidator {

    private static final Set<String> NETWORK_DISABLED_VALUES =
            Set.of("none", "disabled", "deny", "deny-all", "deny_all");

    public void validate(SandboxPolicy policy) {
        if (policy == null) {
            return;
        }

        List<String> violations = new ArrayList<>();
        if (Boolean.TRUE.equals(policy.privileged())) {
            violations.add("privileged mode запрещен");
        }
        if (Boolean.TRUE.equals(policy.hostNetwork())) {
            violations.add("host network запрещен");
        }
        if (Boolean.TRUE.equals(policy.dockerSocketMount())) {
            violations.add("mount Docker socket запрещен");
        }
        if (!policy.hostPaths().isEmpty()) {
            violations.add("hostPath mount запрещен");
        }
        if (requestsNetworkWithoutAllowlist(policy)) {
            violations.add("сетевой egress без allowlist запрещен");
        }

        if (!violations.isEmpty()) {
            throw new ExecutorJobException(
                    ErrorType.SECURITY_ERROR,
                    "executor.sandbox.policy-denied",
                    "Sandbox policy job нарушает требования безопасности",
                    String.join("; ", violations),
                    Map.of("violations", List.copyOf(violations)),
                    ExecutionStatus.FAILED);
        }
    }

    private boolean requestsNetworkWithoutAllowlist(SandboxPolicy policy) {
        if (policy.networkPolicy() == null || policy.networkPolicy().isBlank()) {
            return false;
        }

        String networkPolicy = policy.networkPolicy().trim().toLowerCase(Locale.ROOT);
        return !NETWORK_DISABLED_VALUES.contains(networkPolicy)
                && policy.egressAllowlist().isEmpty();
    }
}
