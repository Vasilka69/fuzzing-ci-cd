package ru.diplom.cicd.fuzzing.runner;

import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.workspace.WorkspaceHandle;

public interface FuzzingKernelAdapter {

    FuzzingKernelExecutionResult run(JobMessage job, WorkspaceHandle workspace, FuzzingParameters parameters);
}
