package ru.diplom.cicd.fuzzing.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJob;
import ru.diplom.cicd.executor.core.job.ExecutorJobContext;
import ru.diplom.cicd.executor.core.job.ExecutorJobResult;
import ru.diplom.cicd.fuzzing.runner.FuzzingKernelAdapter;
import ru.diplom.cicd.fuzzing.runner.FuzzingKernelExecutionResult;
import ru.diplom.cicd.fuzzing.runner.FuzzingParameters;
import ru.diplom.cicd.fuzzing.runner.ProcessFuzzingKernelAdapter;

@Service
public final class FuzzingJob implements ExecutorJob {

    private final FuzzingKernelAdapter kernelAdapter;

    public FuzzingJob(FuzzingKernelAdapter kernelAdapter) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter");
    }

    @Override
    public ExecutorJobResult execute(ExecutorJobContext context) {
        FuzzingParameters parameters = FuzzingParameters.from(context.job());
        FuzzingKernelExecutionResult result = kernelAdapter.run(context.job(), context.workspace(), parameters);
        return new ExecutorJobResult(
                ExecutionStatus.SUCCESS,
                "Fuzzing-ядро завершило запуск адаптера успешно",
                java.util.List.of(),
                metrics(result),
                result.logs(),
                null,
                additionalData(result));
    }

    private Map<String, Object> metrics(FuzzingKernelExecutionResult result) {
        return Map.of(
                "durationMs",
                result.processResult().duration().toMillis(),
                "exitCode",
                result.processResult().exitCode());
    }

    private Map<String, Object> additionalData(FuzzingKernelExecutionResult result) {
        FuzzingParameters parameters = result.parameters();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", parameters.mode().wireValue());
        data.put("localGrammar", parameters.localGrammar());
        data.put("budgetSeconds", parameters.budgetSeconds());
        appendLocalGrammarMetadata(data, parameters);
        result.optionalPrepareCommand().ifPresent(command -> data.put("kernelPrepareCommand", command));
        data.put("kernelCommand", result.command());
        data.put("exitCode", result.processResult().exitCode());
        data.put("durationMs", result.processResult().duration().toMillis());
        data.put("outputLimitBytesPerStream", ProcessFuzzingKernelAdapter.MAX_OUTPUT_BYTES_PER_STREAM);
        data.put("llmWorkerQueueSize", parameters.llmWorkerQueueSize());
        data.put("llmWorkerCount", parameters.llmWorkerCount());
        data.put("maxCandidateChars", parameters.maxCandidateChars());
        data.put("stdoutTruncated", result.processResult().stdoutTruncated());
        data.put("stderrTruncated", result.processResult().stderrTruncated());
        putIfPresent(data, "targetArtifactUri", parameters.targetArtifactUri());
        putIfPresent(data, "sourceSnapshotUri", parameters.sourceSnapshotUri());
        putIfPresent(data, "seedCorpusUri", parameters.seedCorpusUri());
        putIfPresent(data, "dictionaryUri", parameters.dictionaryUri());
        putIfPresent(data, "promptUri", parameters.promptUri());
        data.put("targetCommand", parameters.targetCommand());
        return Map.copyOf(data);
    }

    private void appendLocalGrammarMetadata(Map<String, Object> data, FuzzingParameters parameters) {
        if (!FuzzingParameters.DSL_GRAMMAR.equals(parameters.localGrammar())) {
            return;
        }
        data.put("demoTarget", FuzzingParameters.DSL_GRAMMAR);
        data.put("demoTargetPromptPath", FuzzingParameters.DEFAULT_DSL_PROMPT_FILE);
        data.put("demoTargetSeedCorpusPath", FuzzingParameters.DEFAULT_DSL_SEED_DIR);
        data.put("demoTargetDictionaryPath", FuzzingParameters.DEFAULT_DSL_DICTIONARY_FILE);
    }

    private void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
