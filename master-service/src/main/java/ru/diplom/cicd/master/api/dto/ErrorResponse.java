package ru.diplom.cicd.master.api.dto;

public record ErrorResponse(ErrorBody error, String correlationId) {
    public record ErrorBody(String code, String message, Object details) {}
}
