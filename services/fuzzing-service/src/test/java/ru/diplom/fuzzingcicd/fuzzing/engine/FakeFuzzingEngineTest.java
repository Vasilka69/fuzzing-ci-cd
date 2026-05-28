package ru.diplom.fuzzingcicd.fuzzing.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;
import ru.diplom.fuzzingcicd.contracts.executor.ResourceLimits;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingJobParameters;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.policy.FuzzingPolicyMapper;

class FakeFuzzingEngineTest {

    private final FuzzingEngineRequestFactory requestFactory = new FuzzingEngineRequestFactory();
    private final FuzzingPolicyMapper policyMapper = new FuzzingPolicyMapper();

    @Test
    void drivesNoCrashOutcome() {
        FakeFuzzingEngine engine = new FakeFuzzingEngine(request -> FuzzingEngineResult.completed(
                0,
                List.of(),
                List.of(),
                "storage://fuzzing/reports/job-1.json",
                "storage://fuzzing/corpus/job-1.zip",
                "storage://fuzzing/logs/job-1.txt",
                Map.of("execs_done", 12_000),
                Map.of("fallback_mutations", 10),
                Map.of("generated_candidates", 25)
        ));

        FuzzingEngineResult result = engine.execute(requestFactory.create(
                validEnvelope(),
                validParameters(new FuzzingPolicy(true, false, 1, 10_000, true)),
                Path.of("/workspace/job-1")
        ));

        assertThat(policyMapper.map(result, FuzzingPolicy.defaultPolicy()).status())
                .isEqualTo(ExecutorJobStatus.SUCCEEDED);
    }

    @Test
    void drivesCrashOutcomeThroughPolicy() {
        FakeFuzzingEngine engine = new FakeFuzzingEngine(request -> FuzzingEngineResult.completed(
                0,
                List.of(new FuzzingFinding("storage://fuzzing/crashes/id-000001", "crashes/id-000001", 42)),
                List.of(),
                "storage://fuzzing/reports/job-1.json",
                null,
                "storage://fuzzing/logs/job-1.txt",
                Map.of("unique_crashes", 1),
                Map.of(),
                Map.of()
        ));

        FuzzingEngineResult result = engine.execute(requestFactory.create(
                validEnvelope(),
                validParameters(new FuzzingPolicy(true, false, 1, 0, false)),
                Path.of("/workspace/job-1")
        ));

        assertThat(policyMapper.map(result, new FuzzingPolicy(true, false, 1, 0, false)).status())
                .isEqualTo(ExecutorJobStatus.FAILED);
    }

    private ExecutorJobEnvelope validEnvelope() {
        return new ExecutorJobEnvelope(
                "1",
                "message-1",
                "job-1",
                "fuzzing",
                "templates/fuzzing/default",
                1,
                120,
                new ResourceLimits(1_000, 1024, 2048),
                Map.of()
        );
    }

    private FuzzingJobParameters validParameters(FuzzingPolicy policy) {
        return new FuzzingJobParameters(
                "storage://artifacts/target",
                null,
                "./target @@",
                "storage://fuzzing/seeds.zip",
                "storage://fuzzing/dict.txt",
                "fake",
                60,
                512,
                null,
                null,
                policy
        );
    }
}
