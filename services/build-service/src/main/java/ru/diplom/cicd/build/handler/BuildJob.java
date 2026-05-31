package ru.diplom.cicd.build.handler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.build.artifact.BuildArtifactBundle;
import ru.diplom.cicd.build.artifact.BuildArtifactBundlePublisher;
import ru.diplom.cicd.build.artifact.ExpectedArtifact;
import ru.diplom.cicd.build.artifact.ExpectedArtifactResolver;
import ru.diplom.cicd.build.runner.BuildExecutionResult;
import ru.diplom.cicd.build.runner.BuildParameters;
import ru.diplom.cicd.build.runner.BuildRunner;
import ru.diplom.cicd.build.snapshot.BuildSourceSnapshotPreparer;
import ru.diplom.cicd.build.snapshot.SourceSnapshotWorkspace;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;

@Service
public final class BuildJob implements ExecutorJob {

    private final BuildRunner buildRunner;
    private final BuildSourceSnapshotPreparer sourceSnapshotPreparer;
    private final ExpectedArtifactResolver expectedArtifactResolver;
    private final BuildArtifactBundlePublisher artifactBundlePublisher;

    public BuildJob(
            BuildRunner buildRunner,
            BuildSourceSnapshotPreparer sourceSnapshotPreparer,
            ExpectedArtifactResolver expectedArtifactResolver,
            BuildArtifactBundlePublisher artifactBundlePublisher) {
        this.buildRunner = Objects.requireNonNull(buildRunner, "buildRunner");
        this.sourceSnapshotPreparer = Objects.requireNonNull(sourceSnapshotPreparer, "sourceSnapshotPreparer");
        this.expectedArtifactResolver = Objects.requireNonNull(expectedArtifactResolver, "expectedArtifactResolver");
        this.artifactBundlePublisher = Objects.requireNonNull(artifactBundlePublisher, "artifactBundlePublisher");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        BuildParameters parameters = BuildParameters.from(context.job());
        SourceSnapshotWorkspace snapshotWorkspace = sourceSnapshotPreparer.prepare(
                parameters, context.workspace().root(), context.job().timeoutSeconds());
        BuildExecutionResult result = buildRunner.build(
                parameters, snapshotWorkspace.sourceRoot(), context.job().timeoutSeconds());
        List<ExpectedArtifact> expectedArtifacts = resolveExpectedArtifacts(parameters, snapshotWorkspace);
        BuildArtifactBundle artifactBundle = artifactBundlePublisher.publish(
                context.job(),
                context.workspace().root(),
                workingDirectory(parameters, snapshotWorkspace),
                parameters.expectedArtifactPatterns(),
                expectedArtifacts);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Сборка %s завершена успешно".formatted(parameters.buildTool().wireValue()),
                artifacts(artifactBundle),
                Map.of(
                        "buildTool",
                        parameters.buildTool().wireValue(),
                        "sourceSnapshotUri",
                        parameters.sourceSnapshotUri(),
                        "exitCode",
                        result.processResult().exitCode(),
                        "durationMs",
                        result.processResult().duration().toMillis()),
                logs(snapshotWorkspace, result, artifactBundle),
                null,
                additionalData(result, snapshotWorkspace, expectedArtifacts, artifactBundle));
    }

    private List<ExpectedArtifact> resolveExpectedArtifacts(
            BuildParameters parameters, SourceSnapshotWorkspace snapshotWorkspace) {
        if (parameters.expectedArtifactPatterns().isEmpty()) {
            return List.of();
        }
        Path workingDirectory = workingDirectory(parameters, snapshotWorkspace);
        return expectedArtifactResolver.resolve(workingDirectory, parameters.expectedArtifactPatterns());
    }

    private Path workingDirectory(BuildParameters parameters, SourceSnapshotWorkspace snapshotWorkspace) {
        return snapshotWorkspace
                .sourceRoot()
                .resolve(parameters.workingDirectory())
                .normalize();
    }

    private Map<String, Object> additionalData(
            BuildExecutionResult result,
            SourceSnapshotWorkspace snapshotWorkspace,
            List<ExpectedArtifact> expectedArtifacts,
            BuildArtifactBundle artifactBundle) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("buildTool", result.parameters().buildTool().wireValue());
        data.put("sourceSnapshotUri", result.parameters().sourceSnapshotUri());
        data.put("sourceDirectory", snapshotWorkspace.sourceRoot().getFileName().toString());
        data.put("workingDirectory", workingDirectory(result));
        data.put("entrypoint", result.parameters().entrypoint());
        data.put("args", result.parameters().args());
        data.put("exitCode", result.processResult().exitCode());
        data.put("durationMs", result.processResult().duration().toMillis());
        data.put("outputLimitBytesPerStream", BuildRunner.MAX_OUTPUT_BYTES_PER_STREAM);
        data.put("stdoutTruncated", result.processResult().stdoutTruncated());
        data.put("stderrTruncated", result.processResult().stderrTruncated());
        if (!result.parameters().expectedArtifactPatterns().isEmpty()) {
            data.put("expectedArtifactPatterns", result.parameters().expectedArtifactPatterns());
            data.put(
                    "expectedArtifacts",
                    expectedArtifacts.stream().map(this::expectedArtifactData).toList());
        }
        if (artifactBundle != null) {
            data.put("buildArtifactsBundle", artifactBundle.metadata());
        }
        return Map.copyOf(data);
    }

    private Map<String, Object> expectedArtifactData(ExpectedArtifact artifact) {
        return Map.of(
                "pattern", artifact.pattern(),
                "path", artifact.relativePathText(),
                "sizeBytes", artifact.sizeBytes());
    }

    private String workingDirectory(BuildExecutionResult result) {
        String value = result.parameters().workingDirectory().toString();
        return value.isBlank() ? "." : value;
    }

    private List<ArtifactDescriptor> artifacts(BuildArtifactBundle artifactBundle) {
        if (artifactBundle == null) {
            return List.of();
        }
        return List.of(artifactBundle.artifact());
    }

    private String logs(
            SourceSnapshotWorkspace snapshotWorkspace,
            BuildExecutionResult result,
            BuildArtifactBundle artifactBundle) {
        List<String> chunks = new ArrayList<>();
        chunks.add(snapshotWorkspace.logs());
        chunks.add(result.logs());
        if (artifactBundle != null) {
            chunks.add(artifactBundle.logs());
        }
        return String.join(System.lineSeparator(), chunks);
    }
}
