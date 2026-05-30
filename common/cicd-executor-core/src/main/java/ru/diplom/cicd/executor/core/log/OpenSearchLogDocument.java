package ru.diplom.cicd.executor.core.log;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobType;

record OpenSearchLogDocument(
        String documentId,
        String ingestedAt,
        String sourceService,
        int schemaVersion,
        UUID messageId,
        UUID correlationId,
        UUID pipelineRunId,
        UUID pipelineId,
        UUID stageId,
        UUID jobId,
        UUID jobExecutionId,
        JobType jobType,
        String templatePath,
        EventType eventType,
        ExecutionStatus status,
        int attempt,
        String workerId,
        Long durationMs,
        Map<String, Object> metrics,
        String summary,
        String logs,
        Map<String, Object> additionalData) {

    static OpenSearchLogDocument from(
            ExecutorEventMessage event, String documentId, String sourceService, Instant ingestedAt) {
        return new OpenSearchLogDocument(
                documentId,
                ingestedAt.toString(),
                sourceService,
                event.schemaVersion(),
                event.messageId(),
                event.correlationId(),
                event.pipelineRunId(),
                event.pipelineId(),
                event.stageId(),
                event.jobId(),
                event.jobExecutionId(),
                event.jobType(),
                event.templatePath(),
                event.eventType(),
                event.status(),
                event.attempt(),
                event.workerId(),
                event.durationMs(),
                event.metrics(),
                event.summary(),
                event.logs(),
                logAdditionalData(event.additionalData()));
    }

    private static Map<String, Object> logAdditionalData(Map<String, Object> source) {
        Map<String, Object> data = new LinkedHashMap<>(source);
        data.put("logOnly", true);
        return Collections.unmodifiableMap(data);
    }
}
