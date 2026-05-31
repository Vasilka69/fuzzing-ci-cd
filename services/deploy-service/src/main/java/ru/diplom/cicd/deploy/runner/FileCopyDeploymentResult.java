package ru.diplom.cicd.deploy.runner;

import java.nio.file.Path;

public record FileCopyDeploymentResult(
        FileCopyDeploymentParameters parameters,
        Path downloadedArtifact,
        Path destinationPath,
        long bytesCopied,
        String checksum,
        boolean checksumVerified) {}
