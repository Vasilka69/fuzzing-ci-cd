package ru.diplom.cicd.build.snapshot;

import java.nio.file.Path;
import java.util.Objects;

public record SourceSnapshotWorkspace(Path sourceRoot, Path archivePath, String logs) {

    public SourceSnapshotWorkspace {
        sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot")
                .toAbsolutePath()
                .normalize();
        archivePath = Objects.requireNonNull(archivePath, "archivePath")
                .toAbsolutePath()
                .normalize();
        logs = logs == null ? "" : logs.stripTrailing();
    }
}
