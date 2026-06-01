package ru.diplom.cicd.master.opensearch;

import java.time.OffsetDateTime;
import java.util.Map;

public record ExecutorEventDocument(
        String documentId,
        OffsetDateTime ingestedAt,
        String sourceService,
        String eventType,
        String pipelineId,
        String jobId,
        String jobExecutionId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String logs,
        Map<String, Object> additionalData
) {
}
