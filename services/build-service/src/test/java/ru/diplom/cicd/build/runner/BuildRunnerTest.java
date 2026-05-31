package ru.diplom.cicd.build.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;

class BuildRunnerTest {

    private static final String SOURCE_SNAPSHOT_URI = "storage://source-snapshots/job-1/source-snapshot.tar.gz";

    @TempDir
    private Path tempDir;

    @Test
    void buildRunsMavenWrapperInsideWorkspace() throws Exception {
        createWrapper("mvnw", "maven build ok");
        BuildRunner runner = new BuildRunner(new LocalProcessRunner());
        BuildParameters parameters = new BuildParameters(
                BuildTool.MAVEN, SOURCE_SNAPSHOT_URI, Path.of("."), "./mvnw", List.of("-q", "test"), Map.of());

        BuildExecutionResult result = runner.build(parameters, tempDir, 10);

        assertEquals(0, result.processResult().exitCode());
        assertTrue(result.logs().contains("Сборка maven завершена успешно"));
        assertTrue(result.logs().contains("maven build ok"));
    }

    @Test
    void buildRunsGradleWrapperInsideNestedWorkingDirectory() throws Exception {
        Path moduleDirectory = tempDir.resolve("module-a");
        Files.createDirectories(moduleDirectory);
        createWrapper(moduleDirectory.resolve("gradlew"), "gradle build ok");
        BuildRunner runner = new BuildRunner(new LocalProcessRunner());
        BuildParameters parameters = new BuildParameters(
                BuildTool.GRADLE, SOURCE_SNAPSHOT_URI, Path.of("module-a"), "./gradlew", List.of("build"), Map.of());

        BuildExecutionResult result = runner.build(parameters, tempDir, 10);

        assertEquals(0, result.processResult().exitCode());
        assertTrue(result.logs().contains("Сборка gradle завершена успешно"));
        assertTrue(result.logs().contains("gradle build ok"));
    }

    @Test
    void buildMapsNonZeroExitCodeToUserCodeError() {
        BuildRunner runner = new BuildRunner(new StubProcessRunner(processResult(2, "", "compile failed\n")));
        BuildParameters parameters = new BuildParameters(
                BuildTool.MAVEN, SOURCE_SNAPSHOT_URI, Path.of("."), "./mvnw", List.of("test"), Map.of());

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> runner.build(parameters, tempDir, 10));

        assertEquals(ErrorType.USER_CODE_ERROR, exception.errorType());
        assertEquals("build.process.failed", exception.code());
    }

    private void createWrapper(String name, String output) throws Exception {
        createWrapper(tempDir.resolve(name), output);
    }

    private void createWrapper(Path path, String output) throws Exception {
        Files.writeString(path, "#!/usr/bin/env sh\nprintf '%s\\n' '" + output + "'\n");
        path.toFile().setExecutable(true);
    }

    private static ProcessExecutionResult processResult(int exitCode, String stdout, String stderr) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(
                        processChunk(ProcessStreamType.STDOUT, 0, stdout),
                        processChunk(ProcessStreamType.STDERR, 1, stderr)));
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
}
