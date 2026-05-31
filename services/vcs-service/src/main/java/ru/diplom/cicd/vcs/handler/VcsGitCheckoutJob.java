package ru.diplom.cicd.vcs.handler;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;
import ru.diplom.cicd.vcs.runner.GitCheckoutParameters;
import ru.diplom.cicd.vcs.runner.GitCheckoutResult;
import ru.diplom.cicd.vcs.runner.GitCheckoutRunner;
import ru.diplom.cicd.vcs.snapshot.SourceSnapshotArchive;
import ru.diplom.cicd.vcs.snapshot.SourceSnapshotArchiver;

/**
 * Выполняет MVP-сценарий {@code vcs/git}: shallow checkout и фиксация фактического commit hash.
 */
@Service
public final class VcsGitCheckoutJob implements ExecutorJob {

    static final String TEMPLATE_PATH = "vcs/git";
    private static final String CHECKOUT_DIRECTORY = "source";
    private static final String SNAPSHOT_FILE_NAME = "source-snapshot.tar.gz";

    private final GitCheckoutRunner gitCheckoutRunner;
    private final SourceSnapshotArchiver sourceSnapshotArchiver;

    public VcsGitCheckoutJob(GitCheckoutRunner gitCheckoutRunner, SourceSnapshotArchiver sourceSnapshotArchiver) {
        this.gitCheckoutRunner = Objects.requireNonNull(gitCheckoutRunner, "gitCheckoutRunner");
        this.sourceSnapshotArchiver = Objects.requireNonNull(sourceSnapshotArchiver, "sourceSnapshotArchiver");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        JobMessage job = context.job();
        validateJobRouting(job);

        GitCheckoutParameters parameters = GitCheckoutParameters.from(job);
        Path checkoutPath =
                context.workspace().root().resolve(CHECKOUT_DIRECTORY).normalize();
        GitCheckoutResult checkout = gitCheckoutRunner.checkout(parameters, checkoutPath, job.timeoutSeconds());
        SourceSnapshotArchive snapshot = sourceSnapshotArchiver.create(
                checkoutPath,
                context.workspace().root().resolve(SNAPSHOT_FILE_NAME),
                context.workspace().root(),
                job.timeoutSeconds());

        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Git checkout и архивация source snapshot завершены успешно",
                List.of(),
                Map.of("checkoutDepth", parameters.checkoutDepth(), "snapshotSizeBytes", snapshot.sizeBytes()),
                logs(checkout, snapshot),
                null,
                additionalData(parameters, checkout, snapshot));
    }

    private void validateJobRouting(JobMessage job) {
        if (job.jobType() != JobType.VCS) {
            throw ExecutorJobException.validation("VCS-сервис принимает только jobType=vcs");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation("VCS-сервис сейчас поддерживает только templatePath=vcs/git");
        }
    }

    private Map<String, Object> additionalData(
            GitCheckoutParameters parameters, GitCheckoutResult checkout, SourceSnapshotArchive snapshot) {
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put("vcsType", "git");
        repository.put("repositoryUrl", parameters.safeRepositoryUrl());
        if (parameters.ref() != null) {
            repository.put("ref", parameters.ref());
        }
        repository.put("refType", parameters.refType());
        repository.put("checkoutDepth", parameters.checkoutDepth());
        repository.put("submodules", false);

        Map<String, Object> checkoutData = new LinkedHashMap<>();
        checkoutData.put("commitHash", checkout.commitHash());
        checkoutData.put("relativePath", CHECKOUT_DIRECTORY);

        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("format", snapshot.format());
        snapshotData.put("fileName", snapshot.fileName());
        snapshotData.put("relativePath", snapshot.relativePath());
        snapshotData.put("sizeBytes", snapshot.sizeBytes());
        snapshotData.put("checksumSha256", snapshot.checksumSha256());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vcsType", "git");
        result.put("commitHash", checkout.commitHash());
        result.put("repository", repository);
        result.put("checkout", checkoutData);
        result.put("snapshot", snapshotData);
        return Map.copyOf(result);
    }

    private String logs(GitCheckoutResult checkout, SourceSnapshotArchive snapshot) {
        return checkout.logs() + System.lineSeparator() + snapshot.logs();
    }
}
