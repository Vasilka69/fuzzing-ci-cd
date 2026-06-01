package ru.diplom.cicd.master.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;
import ru.diplom.cicd.master.domain.enums.ExecutorEventType;
import ru.diplom.cicd.master.domain.enums.ExecutorStatus;
import ru.diplom.cicd.master.domain.enums.JobExecutionStatus;
import ru.diplom.cicd.master.repository.JobExecutionRepository;
import ru.diplom.cicd.master.repository.PipelineRunRepository;
import ru.diplom.cicd.master.service.ArtifactService;
import ru.diplom.cicd.master.service.PipelineRunService;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;
import ru.diplom.cicd.master.service.messaging.inbox.InboxDeduplicationService;
import ru.diplom.cicd.master.service.orchestration.JobStateMachine;
import ru.diplom.cicd.master.sse.JobEventSsePublisher;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorEventService {

    private final InboxDeduplicationService inboxDeduplicationService;
    private final JobExecutionRepository jobExecutionRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final JobStateMachine jobStateMachine;
    private final DeadLetterService deadLetterService;
    private final ArtifactService artifactService;
    private final JobEventSsePublisher jobEventSsePublisher;
    private final PipelineRunService pipelineRunService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(
            String consumerName,
            String topic,
            String messageKey,
            String eventSource,
            String sourceDocumentId,
            ExecutorEventMessage message
    ) {
        if (inboxDeduplicationService.isDuplicate(consumerName, eventSource, sourceDocumentId, message.messageId())) {
            return;
        }
        ExecutorEventType eventType = ExecutorEventType.fromValue(message.eventType());
        if (eventType == null) {
            deadLetterService.record("unknown_event_type", message);
            inboxDeduplicationService.registerProcessed(consumerName, topic, messageKey, eventSource, sourceDocumentId, message);
            return;
        }

        JobExecutionEntity execution = jobExecutionRepository.findById(message.jobExecutionId()).orElse(null);
        if (execution == null) {
            log.warn("Skip executor event for missing execution {}", message.jobExecutionId());
            deadLetterService.record("missing_job_execution", message);
            inboxDeduplicationService.registerProcessed(consumerName, topic, messageKey, eventSource, sourceDocumentId, message);
            return;
        }

        JobExecutionStatus current = JobExecutionStatus.fromValue(execution.getStatus());
        JobExecutionStatus target = mapStatus(message.status());
        if (message.status() != null && target == null) {
            deadLetterService.record("unknown_executor_status", message);
        }
        boolean statusChanged = false;
        if (target != null) {
            if (jobStateMachine.canTransition(current, target)) {
                execution.setStatus(target.value());
                statusChanged = true;
            } else {
                deadLetterService.record("invalid_status_transition", message);
            }
        }

        execution.setWorkerId(message.workerId() == null ? execution.getWorkerId() : message.workerId());
        execution.setStartedAt(message.startedAt() == null ? execution.getStartedAt() : message.startedAt());
        execution.setFinishedAt(message.finishedAt() == null ? execution.getFinishedAt() : message.finishedAt());
        execution.setDurationMs(message.durationMs() == null ? execution.getDurationMs() : message.durationMs());
        if (message.metrics() != null) {
            execution.setMetrics(objectMapper.valueToTree(message.metrics()));
        }
        if (message.error() != null) {
            execution.setErrorType(message.error().type());
            execution.setErrorCode(message.error().code());
            execution.setErrorMessage(message.error().message());
            execution.setErrorRetryable(message.error().retryable());
        }
        execution.setUpdatedAt(OffsetDateTime.now());
        jobExecutionRepository.save(execution);

        artifactService.register(message.pipelineRunId(), message.jobExecutionId(), message.artifacts());
        inboxDeduplicationService.registerProcessed(consumerName, topic, messageKey, eventSource, sourceDocumentId, message);
        jobEventSsePublisher.publishEvent(message);

        if (statusChanged && target != null && target.isFinalStatus()) {
            PipelineRunEntity run = pipelineRunRepository.findById(execution.getPipelineRunId()).orElse(null);
            if (run != null) {
                List<JobExecutionEntity> executions = jobExecutionRepository.findByPipelineRunId(run.getId());
                pipelineRunService.dispatchReadyJobs(run, executions);
                pipelineRunService.recomputeRunStatus(run.getId());
            }
        }
    }

    private JobExecutionStatus mapStatus(String status) {
        ExecutorStatus executorStatus = ExecutorStatus.fromValue(status);
        return executorStatus == null ? null : executorStatus.toJobExecutionStatus();
    }
}
