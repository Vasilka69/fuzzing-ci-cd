package ru.diplom.fuzzingcicd.fuzzing.engine;

public interface FuzzingEngine {

    FuzzingEngineResult execute(FuzzingEngineRequest request);
}
