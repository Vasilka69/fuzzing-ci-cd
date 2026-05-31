package ru.diplom.cicd.vcs.snapshot;

import java.nio.file.Path;

public record SourceSnapshotArchive(
        Path path,
        String relativePath,
        String fileName,
        String format,
        long sizeBytes,
        String checksumSha256,
        String logs) {}
