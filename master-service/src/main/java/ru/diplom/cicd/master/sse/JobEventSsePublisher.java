package ru.diplom.cicd.master.sse;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.service.messaging.contract.ExecutorEventMessage;

@Component
@RequiredArgsConstructor
public class JobEventSsePublisher {

    private final SseSessionRegistry sseSessionRegistry;

    public void publishEvent(ExecutorEventMessage eventMessage) {
        sseSessionRegistry.broadcast("job-event", eventMessage, session -> matches(session, eventMessage));
    }

    public void publishHeartbeat() {
        sseSessionRegistry.broadcast("heartbeat", Map.of("ts", System.currentTimeMillis()), session -> true);
    }

    private boolean matches(SseSessionRegistry.Session session, ExecutorEventMessage message) {
        return matchesFilter(session.pipelineRunId(), message.pipelineRunId())
                && matchesFilter(session.jobId(), message.jobId())
                && matchesFilter(session.jobExecutionId(), message.jobExecutionId());
    }

    private boolean matchesFilter(UUID filter, UUID value) {
        return filter == null || filter.equals(value);
    }
}
