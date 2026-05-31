package ru.diplom.cicd.fuzzing.artifact;

import java.util.Map;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;

public record FuzzingArtifactBundle(
        ArtifactDescriptor artifact, FuzzingReport report, Map<String, Object> metadata, String logs) {

    public FuzzingArtifactBundle {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
