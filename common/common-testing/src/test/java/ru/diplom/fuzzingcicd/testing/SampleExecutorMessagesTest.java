package ru.diplom.fuzzingcicd.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobStatus;

class SampleExecutorMessagesTest {

    @Test
    void createsConsistentJobAndResultMessages() {
        var job = SampleExecutorMessages.job("build");
        var result = SampleExecutorMessages.running(job);

        assertEquals(job.jobExecutionId(), result.jobExecutionId());
        assertEquals(job.jobType(), result.jobType());
        assertEquals(ExecutorJobStatus.RUNNING, result.status());
    }
}
