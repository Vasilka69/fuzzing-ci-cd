package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.entity.TriggerEntity;
import ru.diplom.cicd.master.scheduler.TriggerScheduler;

class TriggerSchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesIntervalSlotToDeterministicTime() {
        TriggerScheduler scheduler = new TriggerScheduler(null, appProperties(Duration.ofSeconds(30)), objectMapper);
        TriggerEntity trigger = triggerWithConfig("{\"interval_seconds\":60}");

        OffsetDateTime planned = scheduler.resolvePlannedFireTime(
                trigger,
                OffsetDateTime.of(2026, 5, 31, 10, 7, 42, 0, ZoneOffset.UTC)
        );

        assertEquals(OffsetDateTime.of(2026, 5, 31, 10, 7, 0, 0, ZoneOffset.UTC), planned);
    }

    @Test
    void supportsFivePartCronExpression() {
        TriggerScheduler scheduler = new TriggerScheduler(null, appProperties(Duration.ofSeconds(30)), objectMapper);
        TriggerEntity trigger = triggerWithConfig("{\"cron\":\"*/5 * * * *\",\"timezone\":\"UTC\"}");

        OffsetDateTime planned = scheduler.resolvePlannedFireTime(
                trigger,
                OffsetDateTime.of(2026, 5, 31, 10, 10, 20, 0, ZoneOffset.UTC)
        );

        assertEquals(OffsetDateTime.of(2026, 5, 31, 10, 10, 0, 0, ZoneOffset.UTC), planned);
    }

    @Test
    void invalidCronReturnsNull() {
        TriggerScheduler scheduler = new TriggerScheduler(null, appProperties(Duration.ofSeconds(30)), objectMapper);
        TriggerEntity trigger = triggerWithConfig("{\"cron\":\"bad cron\"}");

        OffsetDateTime planned = scheduler.resolvePlannedFireTime(
                trigger,
                OffsetDateTime.of(2026, 5, 31, 10, 10, 20, 0, ZoneOffset.UTC)
        );

        assertNull(planned);
    }

    private AppProperties appProperties(Duration schedulerInterval) {
        AppProperties properties = new AppProperties();
        AppProperties.Trigger trigger = new AppProperties.Trigger();
        trigger.setSchedulerInterval(schedulerInterval);
        properties.setTrigger(trigger);
        return properties;
    }

    private TriggerEntity triggerWithConfig(String json) {
        try {
            return TriggerEntity.builder()
                    .id(UUID.randomUUID())
                    .pipelineId(UUID.randomUUID())
                    .triggerType("schedule")
                    .isActive(true)
                    .config(objectMapper.readTree(json))
                    .build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
