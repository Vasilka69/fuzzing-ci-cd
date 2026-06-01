package ru.diplom.cicd.master.api.dto.run;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PipelineRunResponse(
        UUID id,
        UUID pipelineId,
        String status,
        UUID correlationId,
        UUID startedBy,
        String triggeredByType,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String summary
) {
}
