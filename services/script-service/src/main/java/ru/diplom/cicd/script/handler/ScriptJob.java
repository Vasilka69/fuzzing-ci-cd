package ru.diplom.cicd.script.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;
import ru.diplom.cicd.script.artifact.ScriptExpectedOutputResolver;
import ru.diplom.cicd.script.artifact.ScriptOutputArtifact;
import ru.diplom.cicd.script.artifact.ScriptOutputPublisher;
import ru.diplom.cicd.script.runner.ScriptExecutionResult;
import ru.diplom.cicd.script.runner.ScriptInputArtifact;
import ru.diplom.cicd.script.runner.ScriptParameters;
import ru.diplom.cicd.script.runner.ScriptRunner;
import ru.diplom.cicd.script.runner.ScriptWorkspace;
import ru.diplom.cicd.script.runner.ScriptWorkspacePreparer;

@Service
public final class ScriptJob implements ExecutorJob {

    private final ScriptWorkspacePreparer workspacePreparer;
    private final ScriptRunner scriptRunner;
    private final ScriptExpectedOutputResolver expectedOutputResolver;
    private final ScriptOutputPublisher outputPublisher;

    public ScriptJob(
            ScriptWorkspacePreparer workspacePreparer,
            ScriptRunner scriptRunner,
            ScriptExpectedOutputResolver expectedOutputResolver,
            ScriptOutputPublisher outputPublisher) {
        this.workspacePreparer = Objects.requireNonNull(workspacePreparer, "workspacePreparer");
        this.scriptRunner = Objects.requireNonNull(scriptRunner, "scriptRunner");
        this.expectedOutputResolver = Objects.requireNonNull(expectedOutputResolver, "expectedOutputResolver");
        this.outputPublisher = Objects.requireNonNull(outputPublisher, "outputPublisher");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        ScriptParameters parameters = ScriptParameters.from(context.job());
        ScriptWorkspace workspace =
                workspacePreparer.prepare(parameters, context.workspace().root());
        ScriptExecutionResult result =
                scriptRunner.run(parameters, workspace, context.job().timeoutSeconds());
        List<ScriptOutputArtifact> expectedOutputs =
                expectedOutputResolver.resolve(workspace.workingDirectory(), parameters.expectedOutputPatterns());
        List<ArtifactDescriptor> outputArtifacts =
                outputPublisher.publish(context.job(), workspace.workingDirectory(), expectedOutputs);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Bash script завершен успешно",
                outputArtifacts,
                metrics(result, outputArtifacts),
                logs(workspace, result, outputArtifacts),
                null,
                additionalData(workspace, result, expectedOutputs, outputArtifacts));
    }

    private Map<String, Object> metrics(ScriptExecutionResult result, List<ArtifactDescriptor> outputArtifacts) {
        return Map.of(
                "exitCode",
                result.processResult().exitCode(),
                "durationMs",
                result.processResult().duration().toMillis(),
                "outputArtifactCount",
                outputArtifacts.size());
    }

    private String logs(ScriptWorkspace workspace, ScriptExecutionResult result, List<ArtifactDescriptor> artifacts) {
        return String.join(
                System.lineSeparator(), inputLogs(workspace), result.logs(), outputPublisher.logs(artifacts));
    }

    private String inputLogs(ScriptWorkspace workspace) {
        if (workspace.inputs().isEmpty()) {
            return "Script input artifacts не заданы";
        }
        return "Script input artifacts скачаны: %d файлов"
                .formatted(workspace.inputs().size());
    }

    private Map<String, Object> additionalData(
            ScriptWorkspace workspace,
            ScriptExecutionResult result,
            List<ScriptOutputArtifact> expectedOutputs,
            List<ArtifactDescriptor> outputArtifacts) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scriptType", "bash");
        data.put("workingDirectory", workingDirectory(result.parameters()));
        data.put("effectiveNetworkPolicy", result.parameters().effectiveNetworkPolicy());
        data.put("sandboxRunner", "executor_container_process");
        data.put("exitCode", result.processResult().exitCode());
        data.put("durationMs", result.processResult().duration().toMillis());
        data.put("outputLimitBytesPerStream", ScriptRunner.MAX_OUTPUT_BYTES_PER_STREAM);
        data.put("stdoutTruncated", result.processResult().stdoutTruncated());
        data.put("stderrTruncated", result.processResult().stderrTruncated());
        data.put(
                "inputArtifacts",
                workspace.inputs().stream().map(this::inputArtifactData).toList());
        data.put("expectedOutputPatterns", result.parameters().expectedOutputPatterns());
        data.put(
                "expectedOutputs",
                expectedOutputs.stream().map(this::expectedOutputData).toList());
        data.put(
                "outputArtifacts",
                outputArtifacts.stream().map(this::outputArtifactData).toList());
        return Map.copyOf(data);
    }

    private String workingDirectory(ScriptParameters parameters) {
        String value = parameters.workingDirectory().toString();
        return value.isBlank() ? "." : value;
    }

    private Map<String, Object> inputArtifactData(ScriptInputArtifact artifact) {
        return Map.of("uri", artifact.uri(), "path", artifact.path().toString().replace('\\', '/'));
    }

    private Map<String, Object> expectedOutputData(ScriptOutputArtifact artifact) {
        return Map.of(
                "pattern", artifact.pattern(),
                "path", artifact.relativePathText(),
                "sizeBytes", artifact.sizeBytes());
    }

    private Map<String, Object> outputArtifactData(ArtifactDescriptor artifact) {
        return Map.of(
                "uri",
                artifact.uri(),
                "name",
                artifact.name(),
                "contentType",
                artifact.contentType(),
                "sizeBytes",
                artifact.sizeBytes(),
                "checksumSha256",
                artifact.checksumSha256());
    }
}
