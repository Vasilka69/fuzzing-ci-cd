package ru.diplom.cicd.master.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final JobEventSsePublisher jobEventSsePublisher;

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval:PT15S}")
    public void heartbeat() {
        jobEventSsePublisher.publishHeartbeat();
    }
}
