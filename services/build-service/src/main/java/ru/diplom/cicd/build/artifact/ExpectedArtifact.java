package ru.diplom.cicd.build.artifact;

import java.nio.file.Path;
import java.util.Objects;

public record ExpectedArtifact(String pattern, Path relativePath, long sizeBytes) {

    public ExpectedArtifact {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Glob-паттерн expected artifact должен быть непустым");
        }
        relativePath = Objects.requireNonNull(relativePath, "relativePath").normalize();
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Путь expected artifact должен быть относительным");
        }
    }

    public String relativePathText() {
        return relativePath.toString().replace('\\', '/');
    }
}
