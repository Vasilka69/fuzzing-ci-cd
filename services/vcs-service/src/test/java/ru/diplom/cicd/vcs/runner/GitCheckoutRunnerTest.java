package ru.diplom.cicd.vcs.runner;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;

class GitCheckoutRunnerTest {

    @TempDir
    private Path tempDir;

    @Test
    void checkoutCreatesShallowGitCloneAndReturnsCommitHash() throws Exception {
        Path repository = createRepository();
        String expectedCommit = git(repository, "rev-parse", "HEAD");
        GitCheckoutParameters parameters = new GitCheckoutParameters(
                repository.toUri().toString(), repository.toUri().toString(), null, "default", 1);
        Path checkoutPath = tempDir.resolve("checkout");

        GitCheckoutResult result =
                new GitCheckoutRunner(new LocalProcessRunner()).checkout(parameters, checkoutPath, 30);

        assertEquals(expectedCommit, result.commitHash());
        assertTrue(Files.isRegularFile(checkoutPath.resolve("README.md")));
        assertEquals("1", git(checkoutPath, "rev-list", "--count", "HEAD"));
        assertTrue(result.logs().contains("Git checkout shallow clone завершен"));
    }

    @Test
    void checkoutRedactsCredentialsFromGitOutput() {
        String repositoryUrl = "https://user:secret-token@example.test/repo.git";
        GitCheckoutParameters parameters = new GitCheckoutParameters(
                repositoryUrl, GitRepositoryUrlRedactor.redact(repositoryUrl), null, "default", 1);
        ProcessRunner processRunner = new SequenceProcessRunner(List.of(
                result(0, "", "Cloning into 'checkout' from https://user:secret-token@example.test/repo.git\n"),
                result(0, "0123456789abcdef\n", "")));

        GitCheckoutResult result =
                new GitCheckoutRunner(processRunner).checkout(parameters, tempDir.resolve("checkout"), 30);

        assertFalse(result.logs().contains("secret-token"));
        assertTrue(result.logs().contains("https://[REDACTED]@example.test/repo.git"));
    }

    @Test
    void checkoutRedactsCredentialsFromFailureDetailsWhenGitShortensUrl() {
        String repositoryUrl = "https://user:secret-token@example.test/repo.git";
        GitCheckoutParameters parameters = new GitCheckoutParameters(
                repositoryUrl, GitRepositoryUrlRedactor.redact(repositoryUrl), null, "default", 1);
        ProcessRunner processRunner = new SequenceProcessRunner(List.of(result(
                128,
                "",
                "fatal: could not read Password for 'https://user:secret-token@example.test': terminal disabled\n")));

        GitCheckoutRunner gitCheckoutRunner = new GitCheckoutRunner(processRunner);
        Path checkoutPath = tempDir.resolve("checkout");
        ExecutorJobException error = assertThrows(
                ExecutorJobException.class, () -> gitCheckoutRunner.checkout(parameters, checkoutPath, 30));

        assertFalse(error.details().contains("secret-token"));
        assertTrue(error.details().contains("https://[REDACTED]@example.test"));
    }

    private Path createRepository() throws Exception {
        Path repository = tempDir.resolve("repo");
        Files.createDirectories(repository);
        git(repository, "init");
        git(repository, "config", "user.email", "ci@example.test");
        git(repository, "config", "user.name", "CI Test");
        Files.writeString(repository.resolve("README.md"), "hello\n");
        git(repository, "add", "README.md");
        git(repository, "commit", "-m", "initial");
        Files.writeString(repository.resolve("README.md"), "hello again\n");
        git(repository, "commit", "-am", "second");
        return repository;
    }

    private String git(Path directory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process =
                new ProcessBuilder(command).directory(directory.toFile()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Git test command failed: " + command + System.lineSeparator() + stderr);
        }
        return stdout.trim();
    }

    private static ProcessExecutionResult result(int exitCode, String stdout, String stderr) {
        return new ProcessExecutionResult(
                exitCode,
                false,
                false,
                Duration.ofMillis(1),
                List.of(chunk(ProcessStreamType.STDOUT, 0, stdout), chunk(ProcessStreamType.STDERR, 1, stderr)));
    }

    private static ProcessOutputChunk chunk(ProcessStreamType stream, long sequence, String text) {
        return new ProcessOutputChunk(stream, sequence, text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class SequenceProcessRunner implements ProcessRunner {

        private final List<ProcessExecutionResult> results;
        private int index;

        private SequenceProcessRunner(List<ProcessExecutionResult> results) {
            this.results = results;
        }

        @Override
        public ProcessExecutionResult run(ProcessExecutionRequest request) {
            if (index >= results.size()) {
                throw new AssertionError("Неожиданный запуск процесса: " + request.command());
            }
            return results.get(index++);
        }
    }
}
