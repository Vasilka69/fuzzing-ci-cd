package ru.diplom.cicd.demo.mockmaster.pipeline;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.demo.mockmaster.config.MockMasterPublisherProperties;

/**
 * Формирует воспроизводимый demo pipeline без зависимости от master-service.
 *
 * <p>UUID строятся от {@code runId}, чтобы повторная публикация одного demo была idempotent для executor-ов, а
 * новый прогон можно было получить через другой {@code runId}.
 */
@SuppressWarnings("java:S1192")
public final class DemoPipelineFactory {

    private static final int SCHEMA_VERSION = 1;
    private static final int ATTEMPT = 1;
    private static final int MAX_ATTEMPTS = 1;

    private final Clock clock;

    public DemoPipelineFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<DemoJobPublication> create(MockMasterPublisherProperties properties) {
        MockMasterPublisherProperties.Topics topics = properties.effectiveTopics();
        String runId = properties.effectiveRunId();
        Instant createdAt = Instant.now(clock);

        UUID correlationId = uuid(runId, "correlation");
        UUID pipelineRunId = uuid(runId, "pipeline-run");
        UUID pipelineId = uuid("demo-pipeline", "pipeline");

        UUID vcsExecutionId = uuid(runId, "job-execution:vcs");
        UUID buildExecutionId = uuid(runId, "job-execution:build");
        UUID fuzzingExecutionId = uuid(runId, "job-execution:fuzzing");
        UUID deployExecutionId = uuid(runId, "job-execution:deploy");
        UUID scriptExecutionId = uuid(runId, "job-execution:script");

        String sourceSnapshotUri = "storage://source-snapshots/%s/source-snapshot.tar.gz".formatted(vcsExecutionId);
        String buildArtifactUri = "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(buildExecutionId);
        String fuzzingReportUri = "storage://fuzzing-reports/%s/fuzzing-report.tar.gz".formatted(fuzzingExecutionId);

        return List.of(
                publication(
                        topics.vcsTopic(),
                        runId,
                        correlationId,
                        pipelineRunId,
                        pipelineId,
                        "vcs",
                        vcsExecutionId,
                        JobType.VCS,
                        "vcs/git",
                        300,
                        Map.of(),
                        Map.of(
                                "vcs_type",
                                "git",
                                "repository_url",
                                "https://example.com/diplom/demo-app.git",
                                "ref",
                                "main",
                                "ref_type",
                                "branch",
                                "checkout_depth",
                                1,
                                "submodules",
                                false),
                        createdAt),
                publication(
                        topics.buildTopic(),
                        runId,
                        correlationId,
                        pipelineRunId,
                        pipelineId,
                        "build",
                        buildExecutionId,
                        JobType.BUILD,
                        "build/maven",
                        600,
                        Map.of("sourceSnapshotUri", sourceSnapshotUri, "dependsOn", List.of(vcsExecutionId)),
                        Map.of(
                                "build_tool",
                                "maven",
                                "source_snapshot_uri",
                                sourceSnapshotUri,
                                "working_directory",
                                ".",
                                "args",
                                List.of("-q", "-DskipTests", "package"),
                                "expected_artifacts",
                                List.of("target/*.jar")),
                        createdAt),
                publication(
                        topics.fuzzingTopic(),
                        runId,
                        correlationId,
                        pipelineRunId,
                        pipelineId,
                        "fuzzing",
                        fuzzingExecutionId,
                        JobType.FUZZING,
                        "fuzzing/afl-llm",
                        120,
                        Map.of(
                                "sourceSnapshotUri",
                                sourceSnapshotUri,
                                "targetArtifactUri",
                                buildArtifactUri,
                                "dependsOn",
                                List.of(buildExecutionId)),
                        Map.of(
                                "mode",
                                "fake",
                                "budget_seconds",
                                30,
                                "local_grammar",
                                "dsl",
                                "source_snapshot_uri",
                                sourceSnapshotUri,
                                "target_artifact_uri",
                                buildArtifactUri,
                                "target_command",
                                List.of("./build/target_dsl")),
                        createdAt),
                publication(
                        topics.deployTopic(),
                        runId,
                        correlationId,
                        pipelineRunId,
                        pipelineId,
                        "deploy",
                        deployExecutionId,
                        JobType.DEPLOY,
                        "deploy/file-copy",
                        300,
                        Map.of("artifactUri", buildArtifactUri, "dependsOn", List.of(buildExecutionId)),
                        Map.of(
                                "deployment_type",
                                "file_copy",
                                "artifact_uri",
                                buildArtifactUri,
                                "environment",
                                "testing",
                                "target",
                                Map.of("destination_path", "releases/demo-app.jar"),
                                "verify_checksum",
                                true,
                                "release_id",
                                releaseId(runId)),
                        createdAt),
                publication(
                        topics.scriptTopic(),
                        runId,
                        correlationId,
                        pipelineRunId,
                        pipelineId,
                        "script",
                        scriptExecutionId,
                        JobType.SCRIPT,
                        "script/bash",
                        180,
                        Map.of(
                                "buildArtifactUri",
                                buildArtifactUri,
                                "fuzzingReportUri",
                                fuzzingReportUri,
                                "dependsOn",
                                List.of(buildExecutionId, fuzzingExecutionId)),
                        Map.of(
                                "script_type",
                                "bash",
                                "script",
                                "set -eu\nmkdir -p out\nprintf 'demo checks passed\\n' > out/demo-check.txt\n",
                                "input_artifacts",
                                List.of(
                                        Map.of("uri", buildArtifactUri, "path", "input/build-artifacts.tar.gz"),
                                        Map.of("uri", fuzzingReportUri, "path", "input/fuzzing-report.tar.gz")),
                                "expected_outputs",
                                List.of("out/*.txt")),
                        createdAt));
    }

    @SuppressWarnings("java:S107")
    private DemoJobPublication publication(
            String topic,
            String runId,
            UUID correlationId,
            UUID pipelineRunId,
            UUID pipelineId,
            String stageName,
            UUID jobExecutionId,
            JobType jobType,
            String templatePath,
            long timeoutSeconds,
            Map<String, Object> inputs,
            Map<String, Object> params,
            Instant createdAt) {
        JobMessage message = new JobMessage(
                SCHEMA_VERSION,
                uuid(runId, "message:" + stageName),
                correlationId,
                pipelineRunId,
                pipelineId,
                uuid("demo-pipeline", "stage:" + stageName),
                uuid("demo-pipeline", "job:" + stageName),
                jobExecutionId,
                jobType,
                templatePath,
                ATTEMPT,
                MAX_ATTEMPTS,
                timeoutSeconds,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                defaultSandboxPolicy(),
                inputs,
                params,
                Map.of("refs", List.of()),
                createdAt);
        return new DemoJobPublication(topic, message);
    }

    private static SandboxPolicy defaultSandboxPolicy() {
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

    private static UUID uuid(String runId, String discriminator) {
        return UUID.nameUUIDFromBytes((runId + ":" + discriminator).getBytes(StandardCharsets.UTF_8));
    }

    private static String releaseId(String runId) {
        String normalized = runId.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        if (normalized.isBlank()) {
            return "demo-release";
        }
        return "demo-" + normalized;
    }
}
