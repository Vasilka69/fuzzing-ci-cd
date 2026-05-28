package ru.diplom.fuzzingcicd.fuzzing.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorErrorType;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.engine.FuzzingEngineResult;
import ru.diplom.fuzzingcicd.fuzzing.engine.FuzzingFinding;

class FuzzingPolicyMapperTest {

    private final FuzzingPolicyMapper mapper = new FuzzingPolicyMapper();

    @Test
    void mapsCrashToFailedWhenPolicyRequiresIt() {
        FuzzingJobOutcome outcome = mapper.map(
                completed(List.of(crash()), List.of()),
                new FuzzingPolicy(true, false, 1, 0, true)
        );

        assertThat(outcome.status()).isEqualTo(ExecutorJobStatus.FAILED);
        assertThat(outcome.error().type()).isEqualTo(ExecutorErrorType.FUZZING_CRASH_FOUND);
        assertThat(outcome.outputs()).containsKey("crash_artifacts");
    }

    @Test
    void keepsCrashAsSuccessfulFindingWhenPolicyAllowsIt() {
        FuzzingJobOutcome outcome = mapper.map(
                completed(List.of(crash()), List.of()),
                new FuzzingPolicy(false, false, 1, 0, true)
        );

        assertThat(outcome.status()).isEqualTo(ExecutorJobStatus.SUCCEEDED);
        assertThat(outcome.error()).isNull();
    }

    @Test
    void mapsHangToFailedWhenPolicyRequiresIt() {
        FuzzingJobOutcome outcome = mapper.map(
                completed(List.of(), List.of(hang())),
                new FuzzingPolicy(false, true, 1, 0, true)
        );

        assertThat(outcome.status()).isEqualTo(ExecutorJobStatus.FAILED);
        assertThat(outcome.error().type()).isEqualTo(ExecutorErrorType.FUZZING_HANG_FOUND);
    }

    @Test
    void mapsEngineStartupErrorToExecutionFailure() {
        FuzzingJobOutcome outcome = mapper.map(
                FuzzingEngineResult.startupError("afl-fuzz executable not found"),
                FuzzingPolicy.defaultPolicy()
        );

        assertThat(outcome.status()).isEqualTo(ExecutorJobStatus.FAILED);
        assertThat(outcome.error().type()).isEqualTo(ExecutorErrorType.ENGINE_STARTUP_ERROR);
    }

    @Test
    void mapsTimeoutAndKeepsPartialArtifacts() {
        FuzzingJobOutcome outcome = mapper.map(
                FuzzingEngineResult.timedOut(
                        List.of(crash()),
                        List.of(),
                        "storage://fuzzing/reports/partial.json",
                        null,
                        "storage://fuzzing/logs/partial.txt",
                        Map.of("execs_done", 500),
                        Map.of(),
                        Map.of()
                ),
                FuzzingPolicy.defaultPolicy()
        );

        assertThat(outcome.status()).isEqualTo(ExecutorJobStatus.TIMED_OUT);
        assertThat(outcome.error().type()).isEqualTo(ExecutorErrorType.ENGINE_TIMEOUT);
        assertThat(outcome.outputs()).containsEntry("fuzzing_report_uri", "storage://fuzzing/reports/partial.json");
    }

    private FuzzingEngineResult completed(List<FuzzingFinding> crashes, List<FuzzingFinding> hangs) {
        return FuzzingEngineResult.completed(
                0,
                crashes,
                hangs,
                "storage://fuzzing/reports/job-1.json",
                "storage://fuzzing/corpus/job-1.zip",
                "storage://fuzzing/logs/job-1.txt",
                Map.of("execs_done", 12_000),
                Map.of("fallback_mutations", 11),
                Map.of("generated_candidates", 25)
        );
    }

    private FuzzingFinding crash() {
        return new FuzzingFinding("storage://fuzzing/crashes/id-000001", "crashes/id-000001", 42);
    }

    private FuzzingFinding hang() {
        return new FuzzingFinding("storage://fuzzing/hangs/id-000001", "hangs/id-000001", 24);
    }
}
