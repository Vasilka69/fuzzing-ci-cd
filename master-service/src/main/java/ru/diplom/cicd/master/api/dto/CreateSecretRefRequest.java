package ru.diplom.cicd.master.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CreateSecretRefRequest(
        @NotBlank String name,
        @NotBlank String provider,
        @NotBlank String externalKey,
        String description,
        String scope,
        JsonNode metadata
) {
}
