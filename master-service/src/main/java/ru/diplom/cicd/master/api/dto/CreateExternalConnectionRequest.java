package ru.diplom.cicd.master.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateExternalConnectionRequest(
        @NotBlank String name,
        @NotBlank String connectionType,
        String url,
        String credentialsRef,
        UUID secretRefId,
        JsonNode config
) {
}
