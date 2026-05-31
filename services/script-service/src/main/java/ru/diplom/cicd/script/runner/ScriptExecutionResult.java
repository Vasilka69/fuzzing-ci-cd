package ru.diplom.cicd.script.runner;

import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;

public record ScriptExecutionResult(ScriptParameters parameters, ProcessExecutionResult processResult, String logs) {}
