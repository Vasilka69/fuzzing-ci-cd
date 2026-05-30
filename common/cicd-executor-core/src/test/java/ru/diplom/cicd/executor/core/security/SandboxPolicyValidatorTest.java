package ru.diplom.cicd.executor.core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class SandboxPolicyValidatorTest {

    private final SandboxPolicyValidator validator = new SandboxPolicyValidator();

    @Test
    void allowsNullAndDefaultSafePolicy() {
        assertDoesNotThrow(() -> validator.validate(null));
        assertDoesNotThrow(() -> validator.validate(safePolicy()));
    }

    @Test
    void rejectsPrivilegedHostNetworkDockerSocketAndHostPath() {
        SandboxPolicy policy = new SandboxPolicy(
                true,
                true,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "none",
                List.of(),
                List.of("/var/run"),
                true,
                Map.of());

        ExecutorJobException error = assertThrows(ExecutorJobException.class, () -> validator.validate(policy));

        assertEquals(ErrorType.SECURITY_ERROR, error.errorType());
        assertEquals(ExecutionStatus.FAILED, error.status());
        assertEquals("executor.sandbox.policy-denied", error.code());
        assertIterableEquals(
                List.of(
                        "privileged mode запрещен",
                        "host network запрещен",
                        "mount Docker socket запрещен",
                        "hostPath mount запрещен"),
                violations(error));
    }

    @Test
    void rejectsNetworkEgressWithoutAllowlist() {
        SandboxPolicy policy = new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "allowlist",
                List.of(),
                List.of(),
                false,
                Map.of());

        ExecutorJobException error = assertThrows(ExecutorJobException.class, () -> validator.validate(policy));

        assertEquals(ErrorType.SECURITY_ERROR, error.errorType());
        assertEquals("сетевой egress без allowlist запрещен", error.details());
    }

    @Test
    void allowsNetworkEgressWithAllowlist() {
        SandboxPolicy policy = new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "allowlist",
                List.of("https://storage.internal"),
                List.of(),
                false,
                Map.of());

        assertDoesNotThrow(() -> validator.validate(policy));
    }

    @SuppressWarnings("unchecked")
    private List<String> violations(ExecutorJobException error) {
        return (List<String>) error.metadata().get("violations");
    }

    private SandboxPolicy safePolicy() {
        return new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "none",
                List.of(),
                List.of(),
                false,
                Map.of());
    }
}
