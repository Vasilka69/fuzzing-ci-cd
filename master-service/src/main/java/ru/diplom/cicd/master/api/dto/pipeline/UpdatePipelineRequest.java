package ru.diplom.cicd.master.api.dto.pipeline;

public record UpdatePipelineRequest(
        String name,
        String description,
        Boolean isActive
) {
}
