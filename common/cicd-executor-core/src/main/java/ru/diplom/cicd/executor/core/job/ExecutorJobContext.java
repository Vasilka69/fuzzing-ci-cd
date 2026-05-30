package ru.diplom.cicd.executor.core.job;

import java.util.Objects;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

/**
 * Контекст выполнения job: исходное сообщение и выделенный workspace.
 */
public record ExecutorJobContext(JobMessage job, WorkspaceHandle workspace) {

    public ExecutorJobContext {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(workspace, "workspace");
    }
}
