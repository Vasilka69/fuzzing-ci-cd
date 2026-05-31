package ru.diplom.cicd.deploy.manifest;

import java.util.Map;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

public record DeploymentManifestResult(ArtifactDescriptor artifact, Map<String, Object> metadata) {

    public DeploymentManifestResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
