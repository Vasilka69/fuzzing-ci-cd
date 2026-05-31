package ru.diplom.cicd.vcs.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class GitCheckoutParametersTest {

    @Test
    void parsesGitCheckoutParamsAndMasksCredentialsInRepositoryUrl() {
        GitCheckoutParameters parameters = GitCheckoutParameters.from(job(Map.of(
                "vcs_type",
                "git",
                "repository_url",
                "https://user:abc123@example.test/repo.git",
                "ref",
                "main",
                "ref_type",
                "branch",
                "checkout_depth",
                "2",
                "submodules",
                false)));

        assertEquals("https://user:abc123@example.test/repo.git", parameters.repositoryUrl());
        assertEquals("https://[REDACTED]@example.test/repo.git", parameters.safeRepositoryUrl());
        assertEquals("main", parameters.ref());
        assertEquals("branch", parameters.refType());
        assertEquals(2, parameters.checkoutDepth());
    }

    @Test
    void rejectsUnsupportedSubmodulesInMvpCheckout() {
        JobMessage job = job(Map.of("repository_url", "https://example.test/repo.git", "submodules", true));
        ExecutorJobException error = assertThrows(
                ExecutorJobException.class,
                () -> GitCheckoutParameters.from(job));

        assertEquals("Git submodules пока не поддерживаются в MVP checkout", error.getMessage());
    }

    @Test
    void rejectsInvalidCheckoutDepth() {
        JobMessage job = job(Map.of("repository_url", "https://example.test/repo.git", "checkout_depth", 0));
        ExecutorJobException error = assertThrows(
                ExecutorJobException.class,
                () -> GitCheckoutParameters.from(job));

        assertEquals("checkout_depth должен быть в диапазоне от 1 до 100", error.getMessage());
    }

    private JobMessage job(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                UUID.fromString("00000000-0000-0000-0000-000000000206"),
                UUID.fromString("00000000-0000-0000-0000-000000000207"),
                JobType.VCS,
                "vcs/git",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                null,
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-30T09:00:00Z"));
    }
}
