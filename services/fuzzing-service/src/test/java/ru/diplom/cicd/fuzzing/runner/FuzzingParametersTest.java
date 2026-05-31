package ru.diplom.cicd.fuzzing.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class FuzzingParametersTest {

    @Test
    void fromReadsSafeCommandAndStorageUris() {
        FuzzingParameters parameters = FuzzingParameters.from(fuzzingJob(Map.of(
                "budget_seconds",
                12,
                "kernel_command",
                List.of("make", "ipc-smoke"),
                "local_grammar",
                "dsl",
                "target_command",
                List.of("./build/target_dsl"),
                "llm_worker_queue_size",
                4,
                "llm_worker_count",
                "2",
                "max_candidate_chars",
                1024,
                "target_artifact_uri",
                "storage://build-artifacts/job/target.tar.gz",
                "seed_corpus_uri",
                "storage://fuzzing-corpus/job/seeds.tar.gz",
                "environment",
                Map.of("AFL_NO_UI", "1"))));

        assertEquals(FuzzingMode.FAKE, parameters.mode());
        assertEquals(12, parameters.budgetSeconds());
        assertEquals("dsl", parameters.localGrammar());
        assertEquals(List.of("make", "ipc-smoke"), parameters.kernelCommand());
        assertEquals(List.of("./build/target_dsl"), parameters.targetCommand());
        assertEquals(4, parameters.llmWorkerQueueSize());
        assertEquals(2, parameters.llmWorkerCount());
        assertEquals(1024, parameters.maxCandidateChars());
        assertEquals("storage://build-artifacts/job/target.tar.gz", parameters.targetArtifactUri());
        assertEquals("storage://fuzzing-corpus/job/seeds.tar.gz", parameters.seedCorpusUri());
        assertEquals(Map.of("AFL_NO_UI", "1"), parameters.environment());
    }

    @Test
    void fromUsesDefaultDemoDslTargetCommand() {
        FuzzingParameters parameters = FuzzingParameters.from(fuzzingJob(Map.of("local_grammar", "dsl")));

        assertEquals(FuzzingParameters.DEFAULT_DSL_TARGET_COMMAND, parameters.targetCommand());
    }

    @Test
    void fromRejectsShellStringKernelCommand() {
        JobMessage job = fuzzingJob(Map.of("kernel_command", "make ipc-smoke"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertTrue(exception.getMessage().contains("kernel_command должен быть массивом строк"));
    }

    @Test
    void fromRejectsBudgetGreaterThanJobTimeout() {
        JobMessage job = fuzzingJob(Map.of("budget_seconds", 31));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertEquals("budget_seconds не должен превышать timeoutSeconds job", exception.getMessage());
    }

    @Test
    void fromRejectsKernelWorkingDirectoryTraversal() {
        JobMessage job = fuzzingJob(Map.of("kernel_working_directory", "../outside"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertTrue(exception.getMessage().contains("kernel_working_directory должен быть относительным"));
    }

    @Test
    void fromRejectsUnsupportedLocalGrammar() {
        JobMessage job = fuzzingJob(Map.of("local_grammar", "json"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertEquals("local_grammar сейчас поддерживает только dsl", exception.getMessage());
    }

    @Test
    void fromRejectsInvalidWorkerQueueSize() {
        JobMessage job = fuzzingJob(Map.of("llm_worker_queue_size", 0));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertEquals("llm_worker_queue_size должен быть положительным", exception.getMessage());
    }

    @Test
    void fromRejectsRealModeWithoutExplicitKernelCommand() {
        JobMessage job = fuzzingJob(Map.of("mode", "real"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> FuzzingParameters.from(job));

        assertTrue(exception.getMessage().contains("mode=real пока требует явный kernel_command"));
    }

    private JobMessage fuzzingJob(Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                UUID.fromString("00000000-0000-0000-0000-000000000402"),
                UUID.fromString("00000000-0000-0000-0000-000000000403"),
                UUID.fromString("00000000-0000-0000-0000-000000000404"),
                UUID.fromString("00000000-0000-0000-0000-000000000405"),
                UUID.fromString("00000000-0000-0000-0000-000000000406"),
                UUID.fromString("00000000-0000-0000-0000-000000000407"),
                JobType.FUZZING,
                FuzzingParameters.TEMPLATE_PATH,
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                params,
                Map.of("refs", List.of()),
                Instant.parse("2026-05-30T09:00:00Z"));
    }

    private SandboxPolicy safeSandboxPolicy() {
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
