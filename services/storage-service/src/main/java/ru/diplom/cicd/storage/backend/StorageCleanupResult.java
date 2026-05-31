package ru.diplom.cicd.storage.backend;

public record StorageCleanupResult(
        String namespacePath, String storageUri, boolean deleted, long deletedCount, long bytesFreed) {}
