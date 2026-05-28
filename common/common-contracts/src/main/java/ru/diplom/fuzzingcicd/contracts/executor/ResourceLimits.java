package ru.diplom.fuzzingcicd.contracts.executor;

public record ResourceLimits(
        Integer cpuMillicores,
        Integer memoryMb,
        Integer ephemeralStorageMb
) {
    public boolean hasMemoryLimit() {
        return memoryMb != null;
    }
}
