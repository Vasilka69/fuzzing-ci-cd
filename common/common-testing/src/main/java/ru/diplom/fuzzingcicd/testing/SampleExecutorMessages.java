package ru.diplom.fuzzingcicd.testing;

import java.util.Map;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorResultEvent;
import ru.diplom.fuzzingcicd.contracts.executor.ResourceLimits;

public final class SampleExecutorMessages {

    private SampleExecutorMessages() {
    }

    public static ExecutorJobEnvelope job(String jobType) {
        return new ExecutorJobEnvelope(
                ExecutorJobEnvelope.CURRENT_SCHEMA_VERSION,
                "message-" + jobType,
                "job-" + jobType,
                jobType,
                "templates/" + jobType + ".yml",
                1,
                300,
                new ResourceLimits(1000, 512, 1024),
                Map.of()
        );
    }

    public static ExecutorResultEvent running(ExecutorJobEnvelope envelope) {
        return new ExecutorResultEvent(
                ExecutorJobEnvelope.CURRENT_SCHEMA_VERSION,
                "result-" + envelope.messageId(),
                envelope.jobExecutionId(),
                envelope.jobType(),
                ExecutorJobStatus.RUNNING,
                null,
                Map.of()
        );
    }
}
