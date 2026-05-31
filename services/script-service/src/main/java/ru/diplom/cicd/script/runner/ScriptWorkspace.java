package ru.diplom.cicd.script.runner;

import java.nio.file.Path;
import java.util.List;

public record ScriptWorkspace(Path root, Path workingDirectory, Path scriptPath, List<ScriptInputArtifact> inputs) {

    public ScriptWorkspace {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
