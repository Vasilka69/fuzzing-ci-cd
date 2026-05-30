package ru.diplom.cicd.executor.core.idempotency;

/**
 * Тип решения idempotency guard-а для текущей доставки job.
 */
public enum IdempotencyDecisionType {
    STARTED,
    DUPLICATE_RUNNING,
    DUPLICATE_COMPLETED
}
