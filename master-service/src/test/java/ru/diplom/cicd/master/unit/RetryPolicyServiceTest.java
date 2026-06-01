package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.service.orchestration.RetryPolicyService;

class RetryPolicyServiceTest {

    private final RetryPolicyService retryPolicyService = new RetryPolicyService();

    @Test
    void allowsRetryWhenMarkedRetryableAndAttemptsRemain() {
        JobEntity job = JobEntity.builder().maxAttempts(3).build();
        JobExecutionEntity execution = JobExecutionEntity.builder()
                .attempt(1)
                .errorRetryable(true)
                .build();

        assertTrue(retryPolicyService.canRetry(execution, job));
    }

    @Test
    void blocksRetryWhenAttemptsExhausted() {
        JobEntity job = JobEntity.builder().maxAttempts(2).build();
        JobExecutionEntity execution = JobExecutionEntity.builder()
                .attempt(2)
                .errorRetryable(true)
                .build();

        assertFalse(retryPolicyService.canRetry(execution, job));
    }

    @Test
    void allowsInfrastructureErrorsByDefault() {
        JobEntity job = JobEntity.builder().maxAttempts(2).build();
        JobExecutionEntity execution = JobExecutionEntity.builder()
                .attempt(1)
                .errorType("infrastructure_error")
                .build();

        assertTrue(retryPolicyService.canRetry(execution, job));
    }

    @Test
    void blocksNonRetryableErrorTypes() {
        JobEntity job = JobEntity.builder().maxAttempts(3).build();
        JobExecutionEntity execution = JobExecutionEntity.builder()
                .attempt(1)
                .errorType("user_code_error")
                .build();

        assertFalse(retryPolicyService.canRetry(execution, job));
    }
}
