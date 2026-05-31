package ru.diplom.cicd.vcs.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;

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
}
