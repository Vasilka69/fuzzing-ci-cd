package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;
import ru.diplom.cicd.master.service.orchestration.JobStateMachine;

class JobStateMachineTest {

    private final JobStateMachine machine = new JobStateMachine();

    @Test
    void allowsQueuedToRunning() {
        assertTrue(machine.canTransition(JobExecutionStatus.QUEUED, JobExecutionStatus.RUNNING));
    }

    @Test
    void blocksFinalToActiveTransition() {
        assertFalse(machine.canTransition(JobExecutionStatus.SUCCESS, JobExecutionStatus.RUNNING));
    }

    @Test
    void allowsRunningToCanceledFlow() {
        assertTrue(machine.canTransition(JobExecutionStatus.RUNNING, JobExecutionStatus.CANCELING));
        assertTrue(machine.canTransition(JobExecutionStatus.CANCELING, JobExecutionStatus.CANCELED));
    }
}
