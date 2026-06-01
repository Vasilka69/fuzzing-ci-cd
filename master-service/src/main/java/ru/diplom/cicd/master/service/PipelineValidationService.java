package ru.diplom.cicd.master.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diplom.cicd.master.domain.entity.JobEntity;
import ru.diplom.cicd.master.domain.entity.StageEntity;
import ru.diplom.cicd.master.repository.JobDependencyRepository;
import ru.diplom.cicd.master.repository.JobRepository;
import ru.diplom.cicd.master.repository.JobTemplateRepository;
import ru.diplom.cicd.master.repository.StageRepository;
import ru.diplom.cicd.master.service.orchestration.ExecutionGraphBuilder;
import ru.diplom.cicd.master.service.orchestration.ExecutionGraphValidator;

@Service
@RequiredArgsConstructor
public class PipelineValidationService {

    private final StageRepository stageRepository;
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final JobTemplateRepository jobTemplateRepository;
    private final ExecutionGraphBuilder executionGraphBuilder;
    private final ExecutionGraphValidator executionGraphValidator;

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID pipelineId) {
        List<StageEntity> stages = stageRepository.findByPipelineIdOrderByPositionAsc(pipelineId);
        List<UUID> stageIds = stages.stream().map(StageEntity::getId).toList();
        List<JobEntity> jobs = stageIds.isEmpty() ? List.of() : jobRepository.findByStageIdInOrderByPositionAsc(stageIds);
        List<UUID> jobIds = jobs.stream().map(JobEntity::getId).toList();
        List<String> errors = new ArrayList<>();

        for (JobEntity job : jobs) {
            if (job.getJobTemplateId() != null) {
                jobTemplateRepository.findById(job.getJobTemplateId()).ifPresent(template -> {
                    if (!template.getJobType().equalsIgnoreCase(job.getJobType())) {
                        errors.add("Job " + job.getId() + " template type mismatch");
                    }
                });
            }
        }

        try {
            var graph = executionGraphBuilder.build(
                    stages,
                    jobs,
                    jobIds.isEmpty() ? List.of() : jobDependencyRepository.findByJobIdIn(jobIds)
            );
            executionGraphValidator.validate(graph);
        } catch (Exception ex) {
            errors.add(ex.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}
