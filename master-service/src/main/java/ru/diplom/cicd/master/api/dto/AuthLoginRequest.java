package ru.diplom.cicd.master.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank String login,
        @NotBlank String password
) {
}
