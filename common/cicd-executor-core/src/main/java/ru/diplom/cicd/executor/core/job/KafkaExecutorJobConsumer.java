package ru.diplom.cicd.executor.core.job;

import java.util.Map;
import java.util.Objects;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import ru.diplom.cicd.contracts.job.JobMessage;

/**
 * Kafka adapter executor-а: выбирает job implementation по {@code templatePath} и подтверждает offset
 * только после завершения общего runtime pipeline.
 */
public final class KafkaExecutorJobConsumer {

    private final ExecutorJobHandler handler;
    private final Map<String, ExecutorJob> jobsByTemplatePath;

    public KafkaExecutorJobConsumer(ExecutorJobHandler handler, Map<String, ExecutorJob> jobsByTemplatePath) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.jobsByTemplatePath = Map.copyOf(Objects.requireNonNull(jobsByTemplatePath, "jobsByTemplatePath"));
    }

    @KafkaListener(topics = "${cicd.executor.topic}")
    public void onJob(JobMessage job, Acknowledgment acknowledgment) {
        Objects.requireNonNull(job, "job");
        ExecutorJob executorJob = jobsByTemplatePath.get(job.templatePath());
        if (executorJob == null) {
            executorJob = unsupportedTemplateJob();
        }
        handler.handle(job, executorJob);
        acknowledgment.acknowledge();
    }

    private ExecutorJob unsupportedTemplateJob() {
        return context -> {
            String templatePath = context.job().templatePath();
            throw ExecutorJobException.validation("Executor не поддерживает templatePath=" + templatePath);
        };
    }
}
