package ru.diplom.cicd.master.api.dto;

import java.util.List;
import java.util.UUID;

public record AuthMeResponse(
        UUID userId,
        String login,
        List<String> roles
) {
}
