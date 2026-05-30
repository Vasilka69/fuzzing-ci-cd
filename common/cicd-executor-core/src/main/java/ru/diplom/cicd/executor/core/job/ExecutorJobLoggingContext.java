package ru.diplom.cicd.executor.core.job;

import java.util.Map;
import org.slf4j.MDC;
import ru.diplom.cicd.contracts.job.JobMessage;

/**
 * MDC-контекст обработки job. Runtime выставляет эти поля на время выполнения executor-а, чтобы
 * structured logging мог связать обычные application logs с конкретной попыткой pipeline.
 */
final class ExecutorJobLoggingContext implements AutoCloseable {

    static final String JOB_EXECUTION_ID = "jobExecutionId";
    static final String CORRELATION_ID = "correlationId";

    private final Map<String, String> previousContext;

    private ExecutorJobLoggingContext(Map<String, String> previousContext) {
        this.previousContext = previousContext;
    }

    static ExecutorJobLoggingContext open(JobMessage job) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        putIfPresent(JOB_EXECUTION_ID, job.jobExecutionId());
        putIfPresent(CORRELATION_ID, job.correlationId());
        return new ExecutorJobLoggingContext(previousContext);
    }

    @Override
    public void close() {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(previousContext);
    }

    private static void putIfPresent(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }
}
