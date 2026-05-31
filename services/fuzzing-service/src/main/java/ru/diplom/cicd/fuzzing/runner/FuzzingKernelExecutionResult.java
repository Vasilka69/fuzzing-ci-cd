package ru.diplom.cicd.fuzzing.runner;

import java.util.List;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;

public record FuzzingKernelExecutionResult(
        FuzzingParameters parameters, List<String> command, ProcessExecutionResult processResult, String logs) {

    public FuzzingKernelExecutionResult {
        command = command == null ? List.of() : List.copyOf(command);
    }
}
