package ru.diplom.cicd.build.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.build.runner.BuildExecutionResult;
import ru.diplom.cicd.build.runner.BuildParameters;
import ru.diplom.cicd.build.runner.BuildRunner;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;

@Service
public final class BuildJob implements ExecutorJob {

    private final BuildRunner buildRunner;

    public BuildJob(BuildRunner buildRunner) {
        this.buildRunner = Objects.requireNonNull(buildRunner, "buildRunner");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        BuildParameters parameters = BuildParameters.from(context.job());
        BuildExecutionResult result = buildRunner.build(
                parameters, context.workspace().root(), context.job().timeoutSeconds());
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Сборка %s завершена успешно".formatted(parameters.buildTool().wireValue()),
                java.util.List.of(),
                Map.of(
                        "buildTool",
                        parameters.buildTool().wireValue(),
                        "exitCode",
                        result.processResult().exitCode(),
                        "durationMs",
                        result.processResult().duration().toMillis()),
                result.logs(),
                null,
                additionalData(result));
    }

    private Map<String, Object> additionalData(BuildExecutionResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("buildTool", result.parameters().buildTool().wireValue());
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
}
