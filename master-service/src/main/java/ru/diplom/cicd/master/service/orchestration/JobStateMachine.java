package ru.diplom.cicd.master.service.orchestration;

import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;

@Component
public class JobStateMachine {

    public boolean canTransition(JobExecutionStatus from, JobExecutionStatus to) {
        if (from == to) {
            return true;
        }
        if (from.isFinalStatus()) {
            return false;
        }
        return switch (from) {
            case QUEUED -> to == JobExecutionStatus.RUNNING
                    || to == JobExecutionStatus.WAITING_APPROVAL
                    || to == JobExecutionStatus.SKIPPED
                    || to == JobExecutionStatus.CANCELING;
            case WAITING_APPROVAL -> to == JobExecutionStatus.QUEUED
                    || to == JobExecutionStatus.SKIPPED
                    || to == JobExecutionStatus.FAILED;
            case RUNNING -> to == JobExecutionStatus.SUCCESS
                    || to == JobExecutionStatus.FAILED
                    || to == JobExecutionStatus.TIMEOUT
                    || to == JobExecutionStatus.CANCELING
                    || to == JobExecutionStatus.CANCELED;
            case CANCELING -> to == JobExecutionStatus.CANCELED
                    || to == JobExecutionStatus.FAILED;
            case RETRYING -> to == JobExecutionStatus.QUEUED;
            default -> false;
        };
    }
}
