package ru.diplom.cicd.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.job.JobType;

class ContractEnumsTest {

    @Test
    void jobTypesMatchWireContract() {
        assertIterableEquals(
                List.of("vcs", "storage", "build", "fuzzing", "deploy", "script"),
                Arrays.stream(JobType.values()).map(JobType::wireValue).toList());
        assertEquals(JobType.BUILD, JobType.fromWireValue("build"));
        assertThrows(IllegalArgumentException.class, () -> JobType.fromWireValue("unknown"));
    }

    @Test
    void eventTypesMatchWireContract() {
        assertIterableEquals(
                List.of(
                        "JOB_ACCEPTED",
                        "JOB_RUNNING",
                        "JOB_PROGRESS",
                        "JOB_ARTIFACT",
                        "JOB_LOG",
                        "JOB_FINISHED",
                        "JOB_SKIPPED",
                        "JOB_CANCELED",
                        "JOB_HEARTBEAT"),
                Arrays.stream(EventType.values()).map(EventType::wireValue).toList());
        assertEquals(EventType.JOB_FINISHED, EventType.fromWireValue("JOB_FINISHED"));
        assertThrows(IllegalArgumentException.class, () -> EventType.fromWireValue("job_finished"));
    }

    @Test
    void executionStatusesMatchWireContract() {
        assertIterableEquals(
                List.of(
                        "QUEUED",
                        "RUNNING",
                        "SUCCESS",
                        "FAILED",
                        "TIMEOUT",
                        "CANCELING",
                        "CANCELED",
                        "RETRYING",
                        "SKIPPED",
                        "WAITING_APPROVAL"),
                Arrays.stream(ExecutionStatus.values())
                        .map(ExecutionStatus::wireValue)
                        .toList());
        assertEquals(ExecutionStatus.SUCCESS, ExecutionStatus.fromWireValue("SUCCESS"));
        assertThrows(IllegalArgumentException.class, () -> ExecutionStatus.fromWireValue("success"));
    }

    @Test
    void errorTypesMatchWireContract() {
        assertIterableEquals(
                List.of(
                        "validation_error",
                        "user_code_error",
                        "infrastructure_error",
                        "timeout",
                        "canceled",
                        "security_error",
                        "fuzzing_crash_found",
                        "cancel_failed",
                        "unknown"),
                Arrays.stream(ErrorType.values()).map(ErrorType::wireValue).toList());
        assertEquals(ErrorType.SECURITY_ERROR, ErrorType.fromWireValue("security_error"));
        assertThrows(IllegalArgumentException.class, () -> ErrorType.fromWireValue("SECURITY_ERROR"));
    }
}
