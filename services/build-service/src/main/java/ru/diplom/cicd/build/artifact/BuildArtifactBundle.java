package ru.diplom.cicd.build.artifact;

import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.artifact.ArtifactDescriptor;
import ru.diplom.cicd.contracts.internal.ContractCollections;

public record BuildArtifactBundle(
        ArtifactDescriptor artifact,
        List<ExpectedArtifact> expectedArtifacts,
        Map<String, Object> metadata,
        String logs) {

    public BuildArtifactBundle {
        expectedArtifacts = ContractCollections.immutableList(expectedArtifacts);
        metadata = ContractCollections.immutableMap(metadata);
    }
}
