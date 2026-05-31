package ru.diplom.cicd.build.runner;

import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;

public record BuildExecutionResult(BuildParameters parameters, ProcessExecutionResult processResult, String logs) {}
