package ru.diplom.cicd.deploy.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentResult;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;

@Service
public final class DeployJob implements ExecutorJob {

    private final FileCopyDeploymentRunner fileCopyDeploymentRunner;

    public DeployJob(FileCopyDeploymentRunner fileCopyDeploymentRunner) {
        this.fileCopyDeploymentRunner = Objects.requireNonNull(fileCopyDeploymentRunner, "fileCopyDeploymentRunner");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        FileCopyDeploymentParameters parameters = FileCopyDeploymentParameters.from(context.job());
        FileCopyDeploymentResult result =
                fileCopyDeploymentRunner.deploy(context.job(), context.workspace(), parameters);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Deploy file-copy завершен успешно",
                List.of(),
                metrics(result),
                logs(result),
                null,
                additionalData(result));
    }

    private Map<String, Object> metrics(FileCopyDeploymentResult result) {
        return Map.of("bytesCopied", result.bytesCopied(), "checksumVerified", result.checksumVerified());
    }

    private String logs(FileCopyDeploymentResult result) {
        return String.join(
                System.lineSeparator(),
                "Deploy file-copy скачал artifact из storage: "
                        + result.parameters().artifactUri(),
                "Deploy file-copy скопировал artifact в target path: " + result.destinationPath(),
                "SHA-256 скопированного artifact: " + result.checksum());
    }

    private Map<String, Object> additionalData(FileCopyDeploymentResult result) {
        FileCopyDeploymentParameters parameters = result.parameters();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deploymentType", "file_copy");
        data.put("artifactUri", parameters.artifactUri());
        data.put("environment", parameters.environment());
        data.put("destinationPath", result.destinationPath().toString());
        data.put("relativeDestinationPath", parameters.destinationPath().toString());
        data.put("bytesCopied", result.bytesCopied());
        data.put("deployedArtifactChecksum", result.checksum());
        data.put("checksumVerified", result.checksumVerified());
        putIfPresent(data, "releaseId", parameters.releaseId());
        putIfPresent(data, "connectionRef", parameters.connectionRef());
        return Map.copyOf(data);
    }

    private void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
