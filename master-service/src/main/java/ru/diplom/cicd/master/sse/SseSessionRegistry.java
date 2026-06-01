package ru.diplom.cicd.master.sse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseSessionRegistry {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SseEmitter register(UUID pipelineRunId, UUID jobId, UUID jobExecutionId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        UUID sessionId = UUID.randomUUID();
        Session session = new Session(sessionId, pipelineRunId, jobId, jobExecutionId, emitter);
        sessions.put(sessionId, session);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(error -> sessions.remove(sessionId));
        return emitter;
    }

    public void broadcast(String eventName, Object payload, Predicate<Session> filter) {
        for (Session session : sessions.values()) {
            if (filter != null && !filter.test(session)) {
                continue;
            }
            try {
                session.emitter().send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException ex) {
                sessions.remove(session.id());
            }
        }
    }

    public record Session(UUID id, UUID pipelineRunId, UUID jobId, UUID jobExecutionId, SseEmitter emitter) {}
}
