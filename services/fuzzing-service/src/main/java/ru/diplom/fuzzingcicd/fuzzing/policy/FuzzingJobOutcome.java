package ru.diplom.fuzzingcicd.fuzzing.policy;

import java.util.Map;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;
import ru.diplom.fuzzingcicd.contracts.executor.JobError;

public record FuzzingJobOutcome(
        ExecutorJobStatus status,
        JobError error,
        Map<String, Object> outputs
) {
    public FuzzingJobOutcome {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
