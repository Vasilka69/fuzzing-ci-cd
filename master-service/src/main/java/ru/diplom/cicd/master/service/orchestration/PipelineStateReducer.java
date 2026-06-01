package ru.diplom.cicd.master.service.orchestration;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;
import ru.diplom.cicd.master.domain.enums.PipelineRunStatus;

@Component
public class PipelineStateReducer {

    public PipelineRunStatus reduce(List<JobExecutionStatus> statuses) {
        if (statuses.isEmpty()) {
            return PipelineRunStatus.QUEUED;
        }

        if (statuses.stream().anyMatch(status -> status == JobExecutionStatus.CANCELING)) {
            return PipelineRunStatus.CANCELING;
        }

        if (statuses.stream().anyMatch(status ->
                status == JobExecutionStatus.QUEUED
                        || status == JobExecutionStatus.RUNNING
                        || status == JobExecutionStatus.RETRYING)) {
            return PipelineRunStatus.RUNNING;
        }

        if (statuses.stream().anyMatch(status -> status == JobExecutionStatus.WAITING_APPROVAL)) {
            return PipelineRunStatus.WAITING_APPROVAL;
        }

        if (statuses.stream().allMatch(status -> status == JobExecutionStatus.CANCELED)) {
            return PipelineRunStatus.CANCELED;
        }

        if (statuses.stream().anyMatch(status -> status == JobExecutionStatus.FAILED || status == JobExecutionStatus.TIMEOUT)) {
            return PipelineRunStatus.FAILED;
        }

        return PipelineRunStatus.SUCCESS;
    }
}
