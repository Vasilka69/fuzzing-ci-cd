package ru.diplom.cicd.build.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class BuildParametersTest {

    private static final String SOURCE_SNAPSHOT_URI = "storage://source-snapshots/job-1/source-snapshot.tar.gz";

    @Test
    void fromReadsMavenParams() {
        BuildParameters parameters = BuildParameters.from(buildJob(
                "build/maven",
                Map.of(
                        "build_tool",
                        "maven",
                        "source_snapshot_uri",
                        SOURCE_SNAPSHOT_URI,
                        "working_directory",
                        "module-a",
                        "entrypoint",
                        "./mvnw",
                        "args",
                        List.of("-q", "test"),
                        "environment",
                        Map.of("MAVEN_OPTS", "-Djava.awt.headless=true"))));

        assertEquals(BuildTool.MAVEN, parameters.buildTool());
        assertEquals(SOURCE_SNAPSHOT_URI, parameters.sourceSnapshotUri());
        assertEquals("module-a", parameters.workingDirectory().toString());
        assertEquals("./mvnw", parameters.entrypoint());
        assertEquals(List.of("-q", "test"), parameters.args());
        assertEquals("-Djava.awt.headless=true", parameters.environment().get("MAVEN_OPTS"));
    }

    @Test
    void fromRejectsParentTraversalWorkingDirectory() {
        JobMessage job = buildJob(
                "build/gradle", Map.of("source_snapshot_uri", SOURCE_SNAPSHOT_URI, "working_directory", "../outside"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> BuildParameters.from(job));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }

    @Test
    void fromRejectsToolMismatch() {
        JobMessage job =
                buildJob("build/maven", Map.of("source_snapshot_uri", SOURCE_SNAPSHOT_URI, "build_tool", "gradle"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> BuildParameters.from(job));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }

    @Test
    void fromRejectsMissingSourceSnapshotUri() {
        JobMessage job = buildJob("build/maven", Map.of("build_tool", "maven"));

        ExecutorJobException exception = assertThrows(ExecutorJobException.class, () -> BuildParameters.from(job));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }

    private JobMessage buildJob(String templatePath, Map<String, Object> params) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                UUID.fromString("00000000-0000-0000-0000-000000000206"),
                UUID.fromString("00000000-0000-0000-0000-000000000207"),
                JobType.BUILD,
                templatePath,
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
