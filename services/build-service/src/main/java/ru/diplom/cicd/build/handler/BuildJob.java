package ru.diplom.cicd.build.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.build.runner.BuildExecutionResult;
import ru.diplom.cicd.build.runner.BuildParameters;
import ru.diplom.cicd.build.runner.BuildRunner;
import ru.diplom.cicd.build.snapshot.BuildSourceSnapshotPreparer;
import ru.diplom.cicd.build.snapshot.SourceSnapshotWorkspace;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;

@Service
public final class BuildJob implements ExecutorJob {

    private final BuildRunner buildRunner;
    private final BuildSourceSnapshotPreparer sourceSnapshotPreparer;

    public BuildJob(BuildRunner buildRunner, BuildSourceSnapshotPreparer sourceSnapshotPreparer) {
        this.buildRunner = Objects.requireNonNull(buildRunner, "buildRunner");
        this.sourceSnapshotPreparer = Objects.requireNonNull(sourceSnapshotPreparer, "sourceSnapshotPreparer");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        BuildParameters parameters = BuildParameters.from(context.job());
        SourceSnapshotWorkspace snapshotWorkspace = sourceSnapshotPreparer.prepare(
                parameters, context.workspace().root(), context.job().timeoutSeconds());
        BuildExecutionResult result = buildRunner.build(
                parameters, snapshotWorkspace.sourceRoot(), context.job().timeoutSeconds());
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Сборка %s завершена успешно".formatted(parameters.buildTool().wireValue()),
                java.util.List.of(),
                Map.of(
                        "buildTool",
                        parameters.buildTool().wireValue(),
                        "sourceSnapshotUri",
                        parameters.sourceSnapshotUri(),
                        "exitCode",
                        result.processResult().exitCode(),
                        "durationMs",
                        result.processResult().duration().toMillis()),
                logs(snapshotWorkspace, result),
                null,
                additionalData(result, snapshotWorkspace));
    }

    private Map<String, Object> additionalData(BuildExecutionResult result, SourceSnapshotWorkspace snapshotWorkspace) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("buildTool", result.parameters().buildTool().wireValue());
        data.put("sourceSnapshotUri", result.parameters().sourceSnapshotUri());
        data.put("sourceDirectory", snapshotWorkspace.sourceRoot().getFileName().toString());
        data.put("workingDirectory", workingDirectory(result));
        data.put("entrypoint", result.parameters().entrypoint());
        data.put("args", result.parameters().args());
        data.put("exitCode", result.processResult().exitCode());
        data.put("durationMs", result.processResult().duration().toMillis());
        return Map.copyOf(data);
    }

    private String workingDirectory(BuildExecutionResult result) {
        String value = result.parameters().workingDirectory().toString();
        return value.isBlank() ? "." : value;
    }

    private String logs(SourceSnapshotWorkspace snapshotWorkspace, BuildExecutionResult result) {
        return snapshotWorkspace.logs() + System.lineSeparator() + result.logs();
    }
}
