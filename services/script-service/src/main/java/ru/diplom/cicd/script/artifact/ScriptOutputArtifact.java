package ru.diplom.cicd.script.artifact;

import java.nio.file.Path;

public record ScriptOutputArtifact(String pattern, Path relativePath, long sizeBytes) {

    public String relativePathText() {
        return relativePath.toString().replace('\\', '/');
    }
}
