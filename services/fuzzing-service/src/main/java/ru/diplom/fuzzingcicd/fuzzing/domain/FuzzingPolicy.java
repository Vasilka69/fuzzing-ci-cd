package ru.diplom.fuzzingcicd.fuzzing.domain;

public record FuzzingPolicy(
        boolean failOnCrash,
        boolean failOnHang,
        int maxCrashes,
        int minExecs,
        boolean saveCorpus
) {
    public static FuzzingPolicy defaultPolicy() {
        return new FuzzingPolicy(true, false, 1, 0, true);
    }
}
