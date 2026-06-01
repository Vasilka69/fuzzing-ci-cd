package ru.diplom.cicd.master.service.orchestration;

import org.springframework.stereotype.Service;
import ru.diplom.cicd.master.domain.enums.ErrorType;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;

@Service
public class RetryPolicyService {

    public boolean canRetry(JobExecutionEntity execution, JobEntity job) {
        if (execution == null || job == null || execution.getAttempt() == null || job.getMaxAttempts() == null) {
            return false;
        }
        if (execution.getAttempt() >= job.getMaxAttempts()) {
            return false;
        }
        if (Boolean.TRUE.equals(execution.getErrorRetryable())) {
            return true;
        }

        ErrorType errorType = ErrorType.fromValue(execution.getErrorType());
        if (errorType == null) {
            return false;
        }
        return errorType == ErrorType.INFRASTRUCTURE_ERROR;
    }
}
