package ru.diplom.cicd.master.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.master.config.AppProperties;
import ru.diplom.cicd.master.domain.entity.TriggerEntity;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.service.TriggerService;

@Component
@RequiredArgsConstructor
@Slf4j
public class TriggerScheduler {

    private final TriggerService triggerService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.trigger.scheduler-interval:PT30S}")
    public void dispatchScheduledTriggers() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (TriggerEntity trigger : triggerService.activeScheduleTriggers()) {
            OffsetDateTime plannedFireTime = resolvePlannedFireTime(trigger, now);
            if (plannedFireTime == null) {
                continue;
            }
            try {
                triggerService.fireScheduled(trigger, plannedFireTime, buildPayload(trigger, plannedFireTime, now));
            } catch (ApiException ex) {
                if ("duplicate_trigger_event".equals(ex.getCode())) {
                    log.debug("Skip duplicate scheduled trigger event: triggerId={}", trigger.getId());
                    continue;
                }
                log.warn("Scheduled trigger dispatch failed: triggerId={}, code={}", trigger.getId(), ex.getCode(), ex);
            } catch (Exception ex) {
                log.error("Scheduled trigger dispatch failed: triggerId={}", trigger.getId(), ex);
            }
        }
    }

    public OffsetDateTime resolvePlannedFireTime(TriggerEntity trigger, OffsetDateTime nowUtc) {
        JsonNode config = trigger.getConfig();
        String cron = readString(config, "cron");
        if (cron != null && !cron.isBlank()) {
            return resolveCronPlannedTime(cron, readString(config, "timezone"), nowUtc);
        }
        long intervalSeconds = readIntervalSeconds(config);
        if (intervalSeconds <= 0) {
            return null;
        }
        long slotEpochSeconds = (nowUtc.toEpochSecond() / intervalSeconds) * intervalSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(slotEpochSeconds), ZoneOffset.UTC);
    }

    private OffsetDateTime resolveCronPlannedTime(String rawCron, String timezone, OffsetDateTime nowUtc) {
        String cron = normalizeCron(rawCron);
        if (cron == null) {
            return null;
        }
        ZoneId zone = parseZoneId(timezone);
        ZonedDateTime nowInZone = nowUtc.atZoneSameInstant(zone);
        Duration lookback = appProperties.getTrigger() == null || appProperties.getTrigger().getSchedulerInterval() == null
                ? Duration.ofSeconds(30)
                : appProperties.getTrigger().getSchedulerInterval();
        ZonedDateTime windowStart = nowInZone.minus(lookback).minusSeconds(1);
        try {
            CronExpression expression = CronExpression.parse(cron);
            ZonedDateTime candidate = expression.next(windowStart);
            if (candidate == null || candidate.isAfter(nowInZone)) {
                return null;
            }
            return candidate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime().withNano(0);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid cron expression: {}", rawCron);
            return null;
        }
    }

    private long readIntervalSeconds(JsonNode config) {
        Integer intervalFromNumber = readPositiveInt(config, "interval_seconds");
        if (intervalFromNumber == null) {
            intervalFromNumber = readPositiveInt(config, "intervalSeconds");
        }
        if (intervalFromNumber == null) {
            intervalFromNumber = readPositiveInt(config, "intervalSec");
        }
        if (intervalFromNumber != null && intervalFromNumber > 0) {
            return intervalFromNumber;
        }
        String intervalValue = readString(config, "interval");
        if (intervalValue != null && !intervalValue.isBlank()) {
            try {
                return Math.max(Duration.parse(intervalValue).toSeconds(), 1);
            } catch (Exception ignored) {
                try {
                    return Math.max(Long.parseLong(intervalValue), 1);
                } catch (NumberFormatException ignoredToo) {
                    return fallbackSchedulerIntervalSeconds();
                }
            }
        }
        return fallbackSchedulerIntervalSeconds();
    }

    private long fallbackSchedulerIntervalSeconds() {
        Duration interval = appProperties.getTrigger() == null
                ? null
                : appProperties.getTrigger().getSchedulerInterval();
        return Math.max(interval == null ? 30 : interval.toSeconds(), 1);
    }

    private Integer readPositiveInt(JsonNode config, String field) {
        if (config == null) {
            return null;
        }
        JsonNode node = config.get(field);
        if (node == null || !node.canConvertToInt()) {
            return null;
        }
        int value = node.intValue();
        return value > 0 ? value : null;
    }

    private String normalizeCron(String rawCron) {
        if (rawCron == null || rawCron.isBlank()) {
            return null;
        }
        String[] parts = rawCron.trim().split("\\s+");
        if (parts.length == 5) {
            return "0 " + rawCron.trim();
        }
        if (parts.length == 6) {
            return rawCron.trim();
        }
        return null;
    }

    private ZoneId parseZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            log.warn("Invalid trigger timezone: {}", timezone);
            return ZoneOffset.UTC;
        }
    }

    private String readString(JsonNode config, String field) {
        if (config == null) {
            return null;
        }
        JsonNode node = config.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private JsonNode buildPayload(TriggerEntity trigger, OffsetDateTime plannedFireTime, OffsetDateTime actualFireTime) {
        ObjectNode payload = trigger.getConfig() != null
                && trigger.getConfig().has("payload")
                && trigger.getConfig().get("payload").isObject()
                ? ((ObjectNode) trigger.getConfig().get("payload")).deepCopy()
                : objectMapper.createObjectNode();
        payload.put("triggerType", "schedule");
        payload.put("triggerId", trigger.getId().toString());
        payload.put("plannedFireTime", plannedFireTime.toString());
        payload.put("firedAt", actualFireTime.toString());
        return payload;
    }
}
