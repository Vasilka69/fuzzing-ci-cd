package ru.diplom.fuzzingcicd.fuzzing.engine;

import java.util.Objects;
import java.util.function.Function;

final class FakeFuzzingEngine implements FuzzingEngine {

    private final Function<FuzzingEngineRequest, FuzzingEngineResult> behavior;

    FakeFuzzingEngine(Function<FuzzingEngineRequest, FuzzingEngineResult> behavior) {
        this.behavior = Objects.requireNonNull(behavior, "behavior must not be null");
    }

    @Override
    public FuzzingEngineResult execute(FuzzingEngineRequest request) {
        return behavior.apply(request);
    }
}
