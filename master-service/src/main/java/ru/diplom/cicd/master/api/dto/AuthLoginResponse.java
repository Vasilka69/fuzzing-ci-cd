package ru.diplom.cicd.master.api.dto;

import java.util.UUID;

public record AuthLoginResponse(
        UUID userId,
        String login,
        String token,
        String tokenType
) {
}
