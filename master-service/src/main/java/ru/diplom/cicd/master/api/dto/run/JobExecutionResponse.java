package ru.diplom.cicd.master.api.dto.run;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobExecutionResponse(
        UUID id,
        UUID pipelineRunId,
        UUID jobId,
        int attempt,
        String status,
        String workerId,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String errorType,
        String errorCode,
        String errorMessage
) {
}
