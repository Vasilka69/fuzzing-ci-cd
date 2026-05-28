package ru.diplom.fuzzingcicd.fuzzing.domain;

import java.util.List;

public record TargetCommand(
        List<String> argv,
        boolean usesFilePlaceholder
) {
    public TargetCommand {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("argv must not be empty");
        }
        argv = List.copyOf(argv);
    }
}
