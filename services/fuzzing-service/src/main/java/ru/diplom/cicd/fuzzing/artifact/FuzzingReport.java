package ru.diplom.cicd.fuzzing.artifact;

import java.util.List;
import java.util.Map;

public record FuzzingReport(
        int schemaVersion,
        String artifactType,
        String jobExecutionId,
        String mode,
        String localGrammar,
        int crashCount,
        int hangCount,
        int corpusCount,
        Map<String, Map<String, String>> fuzzerStats,
        List<FuzzingReportFile> files) {

    public FuzzingReport {
        fuzzerStats = fuzzerStats == null ? Map.of() : Map.copyOf(fuzzerStats);
        files = files == null ? List.of() : List.copyOf(files);
    }
}
