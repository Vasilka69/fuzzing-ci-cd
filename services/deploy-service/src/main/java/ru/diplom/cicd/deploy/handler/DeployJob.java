package ru.diplom.cicd.deploy.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.deploy.manifest.DeploymentManifestPublisher;
import ru.diplom.cicd.deploy.manifest.DeploymentManifestResult;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentParameters;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentResult;
import ru.diplom.cicd.deploy.runner.FileCopyDeploymentRunner;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentParameters;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentResult;
import ru.diplom.cicd.deploy.runner.SshBashDeploymentRunner;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;

@SuppressWarnings("java:S1192")
@Service
public final class DeployJob implements ExecutorJob {

    private final FileCopyDeploymentRunner fileCopyDeploymentRunner;
    private final SshBashDeploymentRunner sshBashDeploymentRunner;
    private final DeploymentManifestPublisher manifestPublisher;

    public DeployJob(
            FileCopyDeploymentRunner fileCopyDeploymentRunner,
            SshBashDeploymentRunner sshBashDeploymentRunner,
            DeploymentManifestPublisher manifestPublisher) {
        this.fileCopyDeploymentRunner = Objects.requireNonNull(fileCopyDeploymentRunner, "fileCopyDeploymentRunner");
        this.sshBashDeploymentRunner = Objects.requireNonNull(sshBashDeploymentRunner, "sshBashDeploymentRunner");
        this.manifestPublisher = Objects.requireNonNull(manifestPublisher, "manifestPublisher");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        if (SshBashDeploymentParameters.TEMPLATE_PATH.equals(context.job().templatePath())) {
            return executeSshBash(context);
        }
        if (!FileCopyDeploymentParameters.TEMPLATE_PATH.equals(context.job().templatePath())) {
            throw ExecutorJobException.validation(
                    "Deploy-сервис сейчас поддерживает templatePath=deploy/file-copy и deploy/ssh-bash");
        }
        FileCopyDeploymentParameters parameters = FileCopyDeploymentParameters.from(context.job());
        FileCopyDeploymentResult result =
                fileCopyDeploymentRunner.deploy(context.job(), context.workspace(), parameters);
        DeploymentManifestResult manifest = manifestPublisher.publish(context.job(), context.workspace(), result);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Deploy file-copy завершен успешно",
                artifacts(manifest),
                metrics(result),
                logs(result),
                null,
                additionalData(result, manifest));
    }

    private ExecutorJobResult executeSshBash(ExecutorJobContext context) {
        SshBashDeploymentParameters parameters = SshBashDeploymentParameters.from(context.job());
        SshBashDeploymentResult result = sshBashDeploymentRunner.deploy(context.job(), context.workspace(), parameters);
        DeploymentManifestResult manifest = manifestPublisher.publish(context.job(), context.workspace(), result);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Deploy ssh-bash завершен успешно",
                artifacts(manifest),
                metrics(result),
                logs(result),
                null,
                additionalData(result, manifest));
    }

    private List<ArtifactDescriptor> artifacts(DeploymentManifestResult manifest) {
        return List.of(manifest.artifact());
    }

    private Map<String, Object> metrics(FileCopyDeploymentResult result) {
        return Map.of("bytesCopied", result.bytesCopied(), "checksumVerified", result.checksumVerified());
    }

    private Map<String, Object> metrics(SshBashDeploymentResult result) {
        return Map.of(
                "bytesCopied",
                result.artifactBytes(),
                "sshCommandCount",
                result.commandResults().size(),
                "backupExisting",
                result.parameters().backupExisting());
    }

    private String logs(FileCopyDeploymentResult result) {
        return String.join(
                System.lineSeparator(),
                "Deploy file-copy скачал artifact из storage: "
                        + result.parameters().artifactUri(),
                "Deploy file-copy скопировал artifact в target path: " + result.destinationPath(),
                "SHA-256 скопированного artifact: " + result.checksum(),
                "Deploy healthcheck: " + result.healthcheck().status());
    }

    private String logs(SshBashDeploymentResult result) {
        return String.join(
                System.lineSeparator(),
                "Deploy ssh-bash скачал artifact из storage: "
                        + result.parameters().artifactUri(),
                "Deploy ssh-bash скопировал artifact через scp в target path: "
                        + result.parameters().destinationPath(),
                "Deploy ssh-bash выполнил команд: " + result.commandResults().size(),
                "SHA-256 отправленного artifact: " + result.artifactChecksum(),
                "Deploy healthcheck: " + result.healthcheck().status());
    }

    private Map<String, Object> additionalData(FileCopyDeploymentResult result, DeploymentManifestResult manifest) {
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
        data.put("healthcheck", result.healthcheck().metadata());
        putIfPresent(data, "releaseId", parameters.releaseId());
        putIfPresent(data, "connectionRef", parameters.connectionRef());
        data.putAll(manifest.metadata());
        return Map.copyOf(data);
    }

    private Map<String, Object> additionalData(SshBashDeploymentResult result, DeploymentManifestResult manifest) {
        SshBashDeploymentParameters parameters = result.parameters();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deploymentType", "ssh_bash");
        data.put("artifactUri", parameters.artifactUri());
        data.put("environment", parameters.environment());
        data.put("targetHost", parameters.target().host());
        data.put("targetPort", parameters.target().port());
        data.put("targetUser", parameters.target().user());
        data.put("destinationPath", parameters.destinationPath().toString());
        data.put("backupExisting", parameters.backupExisting());
        data.put("bytesCopied", result.artifactBytes());
        data.put("deployedArtifactChecksum", result.artifactChecksum());
        // TODO: заменить на true после добавления remote checksum step для ssh-bash target.
        data.put("checksumVerified", false);
        data.put("commandCount", result.commandResults().size());
        data.put("healthcheck", result.healthcheck().metadata());
        putIfPresent(data, "releaseId", parameters.releaseId());
        putIfPresent(data, "credentialsRef", parameters.target().credentialsRef());
        data.putAll(manifest.metadata());
        return Map.copyOf(data);
    }

    private void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
