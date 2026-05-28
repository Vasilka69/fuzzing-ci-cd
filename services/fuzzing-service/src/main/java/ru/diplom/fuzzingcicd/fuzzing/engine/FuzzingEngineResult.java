package ru.diplom.fuzzingcicd.fuzzing.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FuzzingEngineResult(
        FuzzingEngineExitStatus exitStatus,
        Integer exitCode,
        List<FuzzingFinding> crashArtifacts,
        List<FuzzingFinding> hangArtifacts,
        String fuzzingReportUri,
        String corpusUri,
        String logsUri,
        Map<String, Object> aflStats,
        Map<String, Object> mutatorStats,
        Map<String, Object> llmWorkerStats,
        String failureMessage
) {
    public FuzzingEngineResult {
        exitStatus = Objects.requireNonNull(exitStatus, "exitStatus must not be null");
        crashArtifacts = crashArtifacts == null ? List.of() : List.copyOf(crashArtifacts);
        hangArtifacts = hangArtifacts == null ? List.of() : List.copyOf(hangArtifacts);
        aflStats = aflStats == null ? Map.of() : Map.copyOf(aflStats);
        mutatorStats = mutatorStats == null ? Map.of() : Map.copyOf(mutatorStats);
        llmWorkerStats = llmWorkerStats == null ? Map.of() : Map.copyOf(llmWorkerStats);
    }

    public static FuzzingEngineResult completed(
            Integer exitCode,
            List<FuzzingFinding> crashArtifacts,
            List<FuzzingFinding> hangArtifacts,
            String fuzzingReportUri,
            String corpusUri,
            String logsUri,
            Map<String, Object> aflStats,
            Map<String, Object> mutatorStats,
            Map<String, Object> llmWorkerStats
    ) {
        return new FuzzingEngineResult(
                FuzzingEngineExitStatus.COMPLETED,
                exitCode,
                crashArtifacts,
                hangArtifacts,
                fuzzingReportUri,
                corpusUri,
                logsUri,
                aflStats,
                mutatorStats,
                llmWorkerStats,
                null
        );
    }

    public static FuzzingEngineResult timedOut(
            List<FuzzingFinding> crashArtifacts,
            List<FuzzingFinding> hangArtifacts,
            String fuzzingReportUri,
            String corpusUri,
            String logsUri,
            Map<String, Object> aflStats,
            Map<String, Object> mutatorStats,
            Map<String, Object> llmWorkerStats
    ) {
        return new FuzzingEngineResult(
                FuzzingEngineExitStatus.TIMEOUT,
                null,
                crashArtifacts,
                hangArtifacts,
                fuzzingReportUri,
                corpusUri,
                logsUri,
                aflStats,
                mutatorStats,
                llmWorkerStats,
                "fuzzing engine timed out"
        );
    }

    public static FuzzingEngineResult startupError(String failureMessage) {
        return failure(FuzzingEngineExitStatus.STARTUP_ERROR, failureMessage);
    }

    public static FuzzingEngineResult executionError(String failureMessage) {
        return failure(FuzzingEngineExitStatus.EXECUTION_ERROR, failureMessage);
    }

    private static FuzzingEngineResult failure(FuzzingEngineExitStatus exitStatus, String failureMessage) {
        return new FuzzingEngineResult(
                exitStatus,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                failureMessage
        );
    }
}
