package ru.diplom.cicd.deploy.runner;

import java.nio.file.Path;
import java.util.List;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;

public record SshBashDeploymentResult(
        SshBashDeploymentParameters parameters,
        Path downloadedArtifact,
        long artifactBytes,
        String artifactChecksum,
        ProcessExecutionResult copyResult,
        ProcessExecutionResult backupResult,
        List<ProcessExecutionResult> commandResults,
        DeploymentHealthcheckResult healthcheck) {

    public SshBashDeploymentResult {
        commandResults = commandResults == null ? List.of() : List.copyOf(commandResults);
    }
}
