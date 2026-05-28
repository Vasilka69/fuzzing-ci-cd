package ru.diplom.fuzzingcicd.fuzzing.validation;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.diplom.fuzzingcicd.contracts.executor.ExecutorJobEnvelope;
import ru.diplom.fuzzingcicd.contracts.executor.ResourceLimits;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingJobParameters;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingMode;
import ru.diplom.fuzzingcicd.fuzzing.domain.FuzzingPolicy;
import ru.diplom.fuzzingcicd.fuzzing.domain.LlmSettings;

@Component
public class FuzzingJobValidator {

    private static final String JOB_TYPE = "fuzzing";
    private static final String STORAGE_URI_PREFIX = "storage://";
    private static final String CONNECTION_REF_PREFIX = "connection://";

    public FuzzingValidationResult validate(ExecutorJobEnvelope envelope, FuzzingJobParameters parameters) {
        List<FuzzingValidationError> errors = new ArrayList<>();
        validateEnvelope(envelope, errors);
        validateParameters(envelope, parameters, errors);
        return new FuzzingValidationResult(errors);
    }

    private void validateEnvelope(ExecutorJobEnvelope envelope, List<FuzzingValidationError> errors) {
        if (envelope == null) {
            errors.add(new FuzzingValidationError("envelope", "job envelope is required"));
            return;
        }
        if (!ExecutorJobEnvelope.CURRENT_SCHEMA_VERSION.equals(envelope.schemaVersion())) {
            errors.add(new FuzzingValidationError("schema_version", "unsupported schema version"));
        }
        requireText(envelope.messageId(), "message_id", errors);
        requireText(envelope.jobExecutionId(), "job_execution_id", errors);
        if (!JOB_TYPE.equals(envelope.jobType())) {
            errors.add(new FuzzingValidationError("job_type", "job_type must be fuzzing"));
        }
        requireText(envelope.templatePath(), "template_path", errors);
        if (envelope.attempt() < 1) {
            errors.add(new FuzzingValidationError("attempt", "attempt must be greater than zero"));
        }
        if (envelope.timeoutSeconds() < 1) {
            errors.add(new FuzzingValidationError("timeout_seconds", "timeout_seconds must be greater than zero"));
        }
        validateResourceLimits(envelope.resourceLimits(), errors);
    }

    private void validateParameters(
            ExecutorJobEnvelope envelope,
            FuzzingJobParameters parameters,
            List<FuzzingValidationError> errors
    ) {
        if (parameters == null) {
            errors.add(new FuzzingValidationError("parameters", "fuzzing parameters are required"));
            return;
        }
        validateTargetInput(parameters, errors);
        validateTargetCommand(parameters.targetCommand(), errors);
        validateBudget(envelope, parameters, errors);
        validateMode(parameters, errors);
        validateStorageUriIfPresent(parameters.seedCorpusUri(), "seed_corpus_uri", errors);
        validateStorageUriIfPresent(parameters.dictionaryUri(), "dictionary_uri", errors);
        validateStorageUriIfPresent(parameters.promptUri(), "prompt_uri", errors);
        validatePolicy(parameters.policy(), errors);
    }

    private void validateTargetInput(FuzzingJobParameters parameters, List<FuzzingValidationError> errors) {
        boolean hasArtifact = hasText(parameters.targetArtifactUri());
        boolean hasSnapshot = hasText(parameters.sourceSnapshotUri());
        if (hasArtifact == hasSnapshot) {
            errors.add(new FuzzingValidationError(
                    "target_artifact_uri",
                    "exactly one of target_artifact_uri or source_snapshot_uri is required"
            ));
            return;
        }
        validateStorageUriIfPresent(parameters.targetArtifactUri(), "target_artifact_uri", errors);
        validateStorageUriIfPresent(parameters.sourceSnapshotUri(), "source_snapshot_uri", errors);
    }

    private void validateTargetCommand(String targetCommand, List<FuzzingValidationError> errors) {
        try {
            TargetCommandParser.parse(targetCommand);
        } catch (IllegalArgumentException exception) {
            errors.add(new FuzzingValidationError("target_command", exception.getMessage()));
        }
    }

    private void validateBudget(
            ExecutorJobEnvelope envelope,
            FuzzingJobParameters parameters,
            List<FuzzingValidationError> errors
    ) {
        if (parameters.budgetSeconds() < 1) {
            errors.add(new FuzzingValidationError("budget_seconds", "budget_seconds must be greater than zero"));
        }
        if (parameters.memoryLimitMb() < 1) {
            errors.add(new FuzzingValidationError("memory_limit_mb", "memory_limit_mb must be greater than zero"));
        }
        if (envelope != null && envelope.timeoutSeconds() > 0 && parameters.budgetSeconds() > envelope.timeoutSeconds()) {
            errors.add(new FuzzingValidationError(
                    "budget_seconds",
                    "budget_seconds must not exceed timeout_seconds"
            ));
        }
        ResourceLimits limits = envelope == null ? null : envelope.resourceLimits();
        if (limits != null && limits.memoryMb() != null && parameters.memoryLimitMb() > limits.memoryMb()) {
            errors.add(new FuzzingValidationError(
                    "memory_limit_mb",
                    "memory_limit_mb must not exceed resource_limits.memory_mb"
            ));
        }
    }

    private void validateMode(FuzzingJobParameters parameters, List<FuzzingValidationError> errors) {
        FuzzingMode.fromCode(parameters.mode())
                .ifPresentOrElse(
                        mode -> validateModeSpecificSettings(mode, parameters.llm(), errors),
                        () -> errors.add(new FuzzingValidationError("mode", "mode must be fake or real_llm"))
                );
    }

    private void validateModeSpecificSettings(
            FuzzingMode mode,
            LlmSettings llm,
            List<FuzzingValidationError> errors
    ) {
        if (mode != FuzzingMode.REAL_LLM) {
            return;
        }
        if (llm == null) {
            errors.add(new FuzzingValidationError("llm", "llm settings are required for real_llm mode"));
            return;
        }
        if (!hasText(llm.endpointRef())) {
            errors.add(new FuzzingValidationError("llm.endpoint_ref", "endpoint_ref is required for real_llm mode"));
        } else if (!llm.endpointRef().startsWith(CONNECTION_REF_PREFIX)) {
            errors.add(new FuzzingValidationError(
                    "llm.endpoint_ref",
                    "endpoint_ref must use connection:// scheme"
            ));
        }
    }

    private void validatePolicy(FuzzingPolicy policy, List<FuzzingValidationError> errors) {
        if (policy == null) {
            errors.add(new FuzzingValidationError("policy", "policy is required"));
            return;
        }
        if (policy.maxCrashes() < 0) {
            errors.add(new FuzzingValidationError("policy.max_crashes", "max_crashes must not be negative"));
        }
        if (policy.minExecs() < 0) {
            errors.add(new FuzzingValidationError("policy.min_execs", "min_execs must not be negative"));
        }
    }

    private void validateResourceLimits(ResourceLimits resourceLimits, List<FuzzingValidationError> errors) {
        if (resourceLimits == null) {
            return;
        }
        validatePositiveIfPresent(resourceLimits.cpuMillicores(), "resource_limits.cpu_millicores", errors);
        validatePositiveIfPresent(resourceLimits.memoryMb(), "resource_limits.memory_mb", errors);
        validatePositiveIfPresent(resourceLimits.ephemeralStorageMb(), "resource_limits.ephemeral_storage_mb", errors);
    }

    private void validatePositiveIfPresent(Integer value, String field, List<FuzzingValidationError> errors) {
        if (value != null && value < 1) {
            errors.add(new FuzzingValidationError(field, field + " must be greater than zero"));
        }
    }

    private void validateStorageUriIfPresent(String value, String field, List<FuzzingValidationError> errors) {
        if (hasText(value) && !value.startsWith(STORAGE_URI_PREFIX)) {
            errors.add(new FuzzingValidationError(field, field + " must use storage:// scheme"));
        }
    }

    private void requireText(String value, String field, List<FuzzingValidationError> errors) {
        if (!hasText(value)) {
            errors.add(new FuzzingValidationError(field, field + " is required"));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
