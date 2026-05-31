package ru.diplom.cicd.fuzzing.runner;

import java.util.List;
import java.util.Optional;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;

public record FuzzingKernelExecutionResult(
        FuzzingParameters parameters,
        List<String> prepareCommand,
        List<String> command,
        ProcessExecutionResult processResult,
        String logs) {

    public FuzzingKernelExecutionResult {
        prepareCommand = prepareCommand == null ? List.of() : List.copyOf(prepareCommand);
        command = command == null ? List.of() : List.copyOf(command);
    }

    public Optional<List<String>> optionalPrepareCommand() {
        return prepareCommand.isEmpty() ? Optional.empty() : Optional.of(prepareCommand);
    }
}
