package ru.diplom.fuzzingcicd.fuzzing.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorErrorType;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;
import ru.diplom.fuzzingcicd.contracts.executor.JobError;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.engine.FuzzingEngineExitStatus;
import ru.diplom.fuzzingcicd.fuzzing.engine.FuzzingEngineResult;
import ru.diplom.fuzzingcicd.fuzzing.engine.FuzzingFinding;

@Component
public class FuzzingPolicyMapper {

    public FuzzingJobOutcome map(FuzzingEngineResult result, FuzzingPolicy policy) {
        Map<String, Object> outputs = outputs(result);

        if (result.exitStatus() == FuzzingEngineExitStatus.TIMEOUT) {
            return new FuzzingJobOutcome(
                    ExecutorJobStatus.TIMED_OUT,
                    error(ExecutorErrorType.ENGINE_TIMEOUT, "engine_timeout", "fuzzing engine exceeded timeout"),
                    outputs
            );
        }
        if (result.exitStatus() == FuzzingEngineExitStatus.STARTUP_ERROR) {
            return new FuzzingJobOutcome(
                    ExecutorJobStatus.FAILED,
                    error(ExecutorErrorType.ENGINE_STARTUP_ERROR, "engine_startup_error", result.failureMessage()),
                    outputs
            );
        }
        if (result.exitStatus() == FuzzingEngineExitStatus.EXECUTION_ERROR) {
            return new FuzzingJobOutcome(
                    ExecutorJobStatus.FAILED,
                    error(ExecutorErrorType.INFRASTRUCTURE_ERROR, "engine_execution_error", result.failureMessage()),
                    outputs
            );
        }
        if (policy.failOnCrash() && !result.crashArtifacts().isEmpty()) {
            return new FuzzingJobOutcome(
                    ExecutorJobStatus.FAILED,
                    error(ExecutorErrorType.FUZZING_CRASH_FOUND, "fuzzing_crash_found", "target crash was found"),
                    outputs
            );
        }
        if (policy.failOnHang() && !result.hangArtifacts().isEmpty()) {
            return new FuzzingJobOutcome(
                    ExecutorJobStatus.FAILED,
                    error(ExecutorErrorType.FUZZING_HANG_FOUND, "fuzzing_hang_found", "target hang was found"),
                    outputs
            );
        }
        return new FuzzingJobOutcome(ExecutorJobStatus.SUCCEEDED, null, outputs);
    }

    private Map<String, Object> outputs(FuzzingEngineResult result) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        putIfPresent(outputs, "fuzzing_report_uri", result.fuzzingReportUri());
        outputs.put("crash_artifacts", findings(result.crashArtifacts()));
        outputs.put("hang_artifacts", findings(result.hangArtifacts()));
        putIfPresent(outputs, "corpus_uri", result.corpusUri());
        putIfPresent(outputs, "logs_uri", result.logsUri());
        outputs.put("afl_stats", result.aflStats());
        outputs.put("mutator_stats", result.mutatorStats());
        outputs.put("llm_worker_stats", result.llmWorkerStats());
        return outputs;
    }

    private List<Map<String, Object>> findings(List<FuzzingFinding> findings) {
        return findings.stream()
                .map(finding -> {
                    Map<String, Object> output = new LinkedHashMap<>();
                    putIfPresent(output, "artifact_uri", finding.artifactUri());
                    putIfPresent(output, "relative_path", finding.relativePath());
                    output.put("size_bytes", finding.sizeBytes());
                    return Map.copyOf(output);
                })
                .toList();
    }

    private void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private JobError error(ExecutorErrorType type, String code, String message) {
        String safeMessage = message == null || message.isBlank() ? code : message;
        return new JobError(type, code, safeMessage);
    }
}
