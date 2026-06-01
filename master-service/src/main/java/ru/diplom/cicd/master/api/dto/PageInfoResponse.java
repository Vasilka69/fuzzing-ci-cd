package ru.diplom.cicd.master.api.dto;

public record PageInfoResponse(
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext
) {
}
