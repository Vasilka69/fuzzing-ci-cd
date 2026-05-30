package ru.diplom.cicd.executor.core.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProcessRunnerTest {

    private final LocalProcessRunner runner = new LocalProcessRunner();

    @TempDir
    private Path tempDir;

    @Test
    void runReturnsExitCodeAndChunksStdoutStderr() {
        ProcessExecutionResult result = runner.run(ProcessExecutionRequest.builder(javaCommand("streams"))
                .workingDirectory(tempDir)
                .timeout(Duration.ofSeconds(5))
                .outputChunkBytes(4)
                .build());

        assertEquals(7, result.exitCode());
        assertFalse(result.timedOut());
        assertFalse(result.killedAfterGracePeriod());
        assertEquals("stdout-abcdef", result.stdoutText(StandardCharsets.UTF_8));
        assertEquals("stderr-xyz", result.stderrText(StandardCharsets.UTF_8));
        assertTrue(result.outputChunks().stream().allMatch(chunk -> chunk.byteSize() <= 4));
    }

    @Test
    void runPassesArgumentsWithoutShellExpansion() {
        ProcessExecutionResult result =
                runner.run(ProcessExecutionRequest.builder(javaCommand("args", "hello world", "semi;colon", "$HOME"))
                        .workingDirectory(tempDir)
                        .timeout(Duration.ofSeconds(5))
                        .build());

        assertEquals(0, result.exitCode());
        assertEquals("3\nhello world\nsemi;colon\n$HOME\n", result.stdoutText(StandardCharsets.UTF_8));
        assertEquals("", result.stderrText(StandardCharsets.UTF_8));
    }

    @Test
    void runMergesEnvironmentOverridesWhenRequested() {
        ProcessExecutionResult result = runner.run(ProcessExecutionRequest.builder(javaCommand("env", "CICD_TEST_VAR"))
                .workingDirectory(tempDir)
                .environment(Map.of("CICD_TEST_VAR", "visible"))
                .timeout(Duration.ofSeconds(5))
                .build());

        assertEquals(0, result.exitCode());
        assertEquals("visible\n", result.stdoutText(StandardCharsets.UTF_8));
    }

    @Test
    void runTerminatesProcessOnTimeout() {
        ProcessExecutionResult result = runner.run(ProcessExecutionRequest.builder(javaCommand("sleep", "5000"))
                .workingDirectory(tempDir)
                .timeout(Duration.ofMillis(150))
                .gracePeriod(Duration.ofMillis(500))
                .build());

        assertTrue(result.timedOut());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void runForcesTerminationAfterGracePeriodWhenProcessIgnoresTermSignal() {
        Path shell = Path.of("/bin/sh");
        Assumptions.assumeTrue(Files.isExecutable(shell), "Тест forced termination требует /bin/sh");

        ProcessExecutionResult result = runner.run(ProcessExecutionRequest.builder(
                        List.of(shell.toString(), "-c", "trap '' TERM; while true; do sleep 1; done"))
                .workingDirectory(tempDir)
                .timeout(Duration.ofMillis(150))
                .gracePeriod(Duration.ofMillis(150))
                .build());

        assertTrue(result.timedOut());
        assertTrue(result.killedAfterGracePeriod());
    }

    @Test
    void runStreamsChunksToConsumer() {
        List<ProcessOutputChunk> consumedChunks = new ArrayList<>();

        ProcessExecutionResult result = runner.run(ProcessExecutionRequest.builder(javaCommand("streams"))
                .workingDirectory(tempDir)
                .timeout(Duration.ofSeconds(5))
                .outputChunkBytes(5)
                .outputConsumer(consumedChunks::add)
                .build());

        assertEquals(result.outputChunks(), consumedChunks);
    }

    @SuppressWarnings("java:S5778")
    @Test
    void requestRejectsEmptyCommand() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ProcessExecutionRequest.builder(List.of())
                        .timeout(Duration.ofSeconds(1))
                        .build());

        assertEquals("Команда процесса не может быть пустой", exception.getMessage());
    }

    private List<String> javaCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProcessFixture.class.getName());
        command.addAll(List.of(args));
        return command;
    }

    private Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    @SuppressWarnings("java:S2925")
    public static final class ProcessFixture {

        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "streams" -> {
                    System.out.print("stdout-abcdef");
                    System.err.print("stderr-xyz");
                    System.exit(7);
                }
                case "args" -> {
                    System.out.println(args.length - 1);
                    for (int index = 1; index < args.length; index++) {
                        System.out.println(args[index]);
                    }
                }
                case "env" -> System.out.println(System.getenv(args[1]));
                case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
                default -> throw new IllegalArgumentException("Неизвестный режим fixture: " + args[0]);
            }
        }
    }
}
