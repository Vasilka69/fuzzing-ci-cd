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

/**
 * Выполняет MVP-сценарий {@code vcs/git}: shallow checkout и фиксация фактического commit hash.
 */
@Service
public final class VcsGitCheckoutJob implements ExecutorJob {

    static final String TEMPLATE_PATH = "vcs/git";
    private static final String CHECKOUT_DIRECTORY = "source";

    private final GitCheckoutRunner gitCheckoutRunner;

    public VcsGitCheckoutJob(GitCheckoutRunner gitCheckoutRunner) {
        this.gitCheckoutRunner = Objects.requireNonNull(gitCheckoutRunner, "gitCheckoutRunner");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        JobMessage job = context.job();
        validateJobRouting(job);

        GitCheckoutParameters parameters = GitCheckoutParameters.from(job);
        Path checkoutPath =
                context.workspace().root().resolve(CHECKOUT_DIRECTORY).normalize();
        GitCheckoutResult checkout = gitCheckoutRunner.checkout(parameters, checkoutPath, job.timeoutSeconds());

        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Git checkout завершен успешно",
                List.of(),
                Map.of("checkoutDepth", parameters.checkoutDepth()),
                checkout.logs(),
                null,
                additionalData(parameters, checkout));
    }

    private void validateJobRouting(JobMessage job) {
        if (job.jobType() != JobType.VCS) {
            throw ExecutorJobException.validation("VCS-сервис принимает только jobType=vcs");
        }
        if (!TEMPLATE_PATH.equals(job.templatePath())) {
            throw ExecutorJobException.validation("VCS-сервис сейчас поддерживает только templatePath=vcs/git");
        }
    }

    private Map<String, Object> additionalData(GitCheckoutParameters parameters, GitCheckoutResult checkout) {
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vcsType", "git");
        result.put("commitHash", checkout.commitHash());
        result.put("repository", repository);
        result.put("checkout", checkoutData);
        return Map.copyOf(result);
    }
}
