package ru.diplom.cicd.executor.core.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.error.ExecutorError;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;

/**
 * Общие runtime-метрики executor job. Имена метрик намеренно стабильные: на них
 * могут опираться actuator endpoints, dashboards и demo-сценарии.
 */
public final class ExecutorJobMetrics {

    public static final String JOBS_TOTAL = "cicd.executor.jobs.total";
    public static final String JOBS_DURATION = "cicd.executor.jobs.duration";
    public static final String JOBS_FAILURES_TOTAL = "cicd.executor.jobs.failures.total";
    public static final String JOBS_ACTIVE = "cicd.executor.jobs.active";

    public static final String JOB_TYPE_TAG = "jobType";
    public static final String TEMPLATE_PATH_TAG = "templatePath";
    public static final String STATUS_TAG = "status";
    public static final String UNKNOWN_JOB_TYPE = "UNKNOWN";

    private static final ExecutorJobMetrics NOOP = new ExecutorJobMetrics();

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeJobs;

    private ExecutorJobMetrics() {
        this.meterRegistry = null;
        this.activeJobs = null;
    }

    public ExecutorJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.activeJobs = new AtomicInteger();
        Gauge.builder(JOBS_ACTIVE, activeJobs, AtomicInteger::get)
                .description("Количество executor job, которые сейчас обрабатываются")
                .register(meterRegistry);
    }

    public static ExecutorJobMetrics noop() {
        return NOOP;
    }

    public void jobStarted() {
        if (activeJobs != null) {
            activeJobs.incrementAndGet();
        }
    }

    public void jobFinished(JobMessage job, ExecutorEventMessage event) {
        if (meterRegistry == null) {
            return;
        }

        String jobType = job.jobType() != null ? job.jobType().name() : UNKNOWN_JOB_TYPE;
        String templatePath =
                job.templatePath() != null && !job.templatePath().isBlank() ? job.templatePath() : UNKNOWN_JOB_TYPE;
        String status = event.status() != null ? event.status().name() : UNKNOWN_JOB_TYPE;

        Counter.builder(JOBS_TOTAL)
                .description("Количество завершенных executor job")
                .tag(JOB_TYPE_TAG, jobType)
                .tag(TEMPLATE_PATH_TAG, templatePath)
                .tag(STATUS_TAG, status)
                .register(meterRegistry)
                .increment();

        Timer.builder(JOBS_DURATION)
                .description("Длительность обработки executor job")
                .tag(JOB_TYPE_TAG, jobType)
                .tag(TEMPLATE_PATH_TAG, templatePath)
                .tag(STATUS_TAG, status)
                .register(meterRegistry)
                .record(duration(event));

        ExecutorError error = event.error();
        if (error != null) {
            ErrorType errorType = error.type() != null ? error.type() : ErrorType.UNKNOWN;
            Counter.builder(JOBS_FAILURES_TOTAL)
                    .description("Количество завершенных с ошибкой executor job по типу ошибки")
                    .tag(JOB_TYPE_TAG, jobType)
                    .tag(TEMPLATE_PATH_TAG, templatePath)
                    .tag("errorType", errorType.name())
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void jobStopped() {
        if (activeJobs != null) {
            activeJobs.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private Duration duration(ExecutorEventMessage event) {
        Long durationMs = event.durationMs();
        if (durationMs == null || durationMs < 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(durationMs);
    }
}
