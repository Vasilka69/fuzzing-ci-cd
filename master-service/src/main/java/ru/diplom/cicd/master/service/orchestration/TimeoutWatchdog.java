package ru.diplom.cicd.master.service.orchestration;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.repository.JobExecutionRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeoutWatchdog {

    private final JobExecutionRepository jobExecutionRepository;

    @Scheduled(fixedDelayString = "PT30S")
    @Transactional
    public void checkTimeouts() {
        List<JobExecutionEntity> running = jobExecutionRepository.findAll().stream()
                .filter(e -> "running".equals(e.getStatus()))
                .toList();
        OffsetDateTime now = OffsetDateTime.now();
        for (JobExecutionEntity execution : running) {
            if (execution.getStartedAt() == null) {
                continue;
            }
            long elapsed = java.time.Duration.between(execution.getStartedAt(), now).toSeconds();
            if (execution.getDurationMs() != null && elapsed > execution.getDurationMs() / 1000L) {
                execution.setStatus("timeout");
                execution.setFinishedAt(now);
                execution.setUpdatedAt(now);
                jobExecutionRepository.save(execution);
                log.warn("Job execution {} moved to timeout by watchdog", execution.getId());
            }
        }
    }
}
