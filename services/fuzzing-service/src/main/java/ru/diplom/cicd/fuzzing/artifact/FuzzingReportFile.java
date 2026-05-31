package ru.diplom.cicd.fuzzing.artifact;

public record FuzzingReportFile(String category, String path, long sizeBytes) {}
