package ru.diplom.cicd.script.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;

class ScriptRunnerTest {

    @TempDir
    private Path tempDir;

    @Test
    void runMapsNonZeroExitCodeToUserCodeError() {
        ScriptRunner runner = new ScriptRunner(new StubProcessRunner(processResult(7, "", "script failed\n")));
        ScriptParameters parameters = inlineParameters("printf 'fail\\n'");
        ScriptWorkspace workspace = new ScriptWorkspace(tempDir, tempDir, tempDir.resolve("script.sh"), List.of());

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> runner.run(parameters, workspace, 10));

        assertEquals(ErrorType.USER_CODE_ERROR, exception.errorType());
        assertEquals("script.process.failed", exception.code());
    }

    @Test
    void runRequestsOutputLimitAndAddsTruncationMarkers() {
        CapturingProcessRunner processRunner = new CapturingProcessRunner(
                processResult(0, "abcde", "12345", Set.of(ProcessStreamType.STDOUT, ProcessStreamType.STDERR)));
        ScriptRunner runner = new ScriptRunner(processRunner);
        ScriptParameters parameters = inlineParameters("printf 'ok\\n'");
        ScriptWorkspace workspace = new ScriptWorkspace(tempDir, tempDir, tempDir.resolve("script.sh"), List.of());

        ScriptExecutionResult result = runner.run(parameters, workspace, 10);

        assertEquals(ScriptRunner.MAX_OUTPUT_BYTES_PER_STREAM, processRunner.request.maxOutputBytesPerStream());
        assertEquals(List.of("bash", tempDir.resolve("script.sh").toString()), processRunner.request.command());
        assertTrue(result.logs().contains("[stdout усечен: сохранено не более 65536 байт]"));
        assertTrue(result.logs().contains("[stderr усечен: сохранено не более 65536 байт]"));
    }

    private ScriptParameters inlineParameters(String script) {
        return new ScriptParameters(script, null, Path.of("."), Map.of(), List.of(), List.of(), "none");
    }

    private static ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return processResult(exitCode, stdout, stderr, Set.of());
    }

    private static ProcessExecutionResult processResult(
            int exitCode, String stdout, String stderr, Set<ProcessStreamType> truncatedStreams) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(
                        processChunk(ProcessStreamType.STDOUT, 0, stdout),
                        processChunk(ProcessStreamType.STDERR, 1, stderr)),
                truncatedStreams);
    }

    private static ProcessOutputChunk processChunk(ProcessStreamType stream, long sequence, String text) {
        return new ProcessOutputChunk(stream, sequence, text.getBytes(StandardCharsets.UTF_8));
    }

    private record StubProcessRunner(ProcessExecutionResult result) implements ProcessRunner {

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            return result;
        }
    }

    private static final class CapturingProcessRunner implements ProcessRunner {

        private final ProcessExecutionResult result;
        private ProcessExecutionRequest request;

        private CapturingProcessRunner(ProcessExecutionResult result) {
            this.result = result;
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            this.request = request;
            return result;
        }
    }
}
