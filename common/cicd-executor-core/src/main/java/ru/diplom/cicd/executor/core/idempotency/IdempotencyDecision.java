package ru.diplom.cicd.executor.core.idempotency;

import java.time.Instant;
import java.util.Map;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.internal.ContractCollections;

/**
 * Результат проверки {@code jobExecutionId}: можно ли выполнять job или доставка уже является
 * дубликатом существующей/завершенной попытки.
 */
public record IdempotencyDecision(
        IdempotencyDecisionType type,
        ExecutionStatus previousStatus,
        String summary,
        Instant updatedAt,
        Map<String, Object> metadata) {

    public IdempotencyDecision {
        metadata = ContractCollections.immutableMap(metadata);
    }

    public boolean shouldExecute() {
        return type == IdempotencyDecisionType.STARTED;
    }

    public static IdempotencyDecision started() {
        return new IdempotencyDecision(
                IdempotencyDecisionType.STARTED, ExecutionStatus.RUNNING, "Job принята к выполнению", null, Map.of());
    }

    public static IdempotencyDecision duplicateRunning(Instant updatedAt) {
        return new IdempotencyDecision(
                IdempotencyDecisionType.DUPLICATE_RUNNING,
                ExecutionStatus.RUNNING,
                "Повторная доставка job пропущена: job уже выполняется",
                updatedAt,
                Map.of());
    }

    public static IdempotencyDecision duplicateCompleted(
            ExecutionStatus previousStatus, String summary, Instant updatedAt, Map<String, Object> metadata) {
        return new IdempotencyDecision(
                IdempotencyDecisionType.DUPLICATE_COMPLETED,
                previousStatus,
                summary == null || summary.isBlank()
                        ? "Повторная доставка job пропущена: результат уже зафиксирован"
                        : "Повторная доставка job пропущена: " + summary,
                updatedAt,
                metadata);
    }
}
