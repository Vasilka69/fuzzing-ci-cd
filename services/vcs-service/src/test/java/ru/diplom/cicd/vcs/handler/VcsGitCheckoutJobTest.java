package ru.diplom.cicd.vcs.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.event.EventType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.contracts.event.ExecutorEventMessage;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.contracts.job.ResourceLimits;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;
import ru.diplom.cicd.contracts.security.SandboxPolicy;
import ru.diplom.cicd.executor.core.event.ExecutorEventPublisher;
import ru.diplom.cicd.executor.core.job.ExecutorJobHandler;
import ru.diplom.cicd.executor.core.log.ExecutorLogPublisher;
import ru.diplom.cicd.executor.core.process.LocalProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessExecutionRequest;
import ru.diplom.cicd.executor.core.process.ProcessExecutionResult;
import ru.diplom.cicd.executor.core.process.ProcessOutputChunk;
import ru.diplom.cicd.executor.core.process.ProcessRunner;
import ru.diplom.cicd.executor.core.process.ProcessStreamType;
import ru.diplom.cicd.executor.core.security.SecretRedactor;
import ru.diplom.cicd.executor.core.workspace.LocalWorkspaceManager;
import ru.diplom.cicd.vcs.runner.GitCheckoutRunner;
import ru.diplom.cicd.vcs.snapshot.SourceSnapshotArchiver;

class VcsGitCheckoutJobTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000107");

    @TempDir
    private Path tempDir;

    @SuppressWarnings("java:S5961")
    @Test
    void handleGitJobPublishesFinishedEventWithoutInlineLogs() throws Exception {
        Path repository = createRepository();
        String expectedCommit = git(repository, "rev-parse", "HEAD");
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "vcs-test-worker-1");
        LocalProcessRunner processRunner = new LocalProcessRunner();
        VcsGitCheckoutJob job =
                new VcsGitCheckoutJob(new GitCheckoutRunner(processRunner), new SourceSnapshotArchiver(processRunner));

        ExecutorEventMessage finishedEvent = handler.handle(vcsJob(repository), job);

        assertEquals(2, eventPublisher.events.size());
        assertEquals(EventType.JOB_RUNNING, eventPublisher.events.getFirst().eventType());
        assertEquals(EventType.JOB_FINISHED, finishedEvent.eventType());
        assertEquals(ExecutionStatus.SUCCESS, finishedEvent.status());
        assertEquals("Git checkout и архивация source snapshot завершены успешно", finishedEvent.summary());
        assertEquals(expectedCommit, finishedEvent.additionalData().get("commitHash"));
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot =
                (Map<String, Object>) finishedEvent.additionalData().get("snapshot");
        assertNotNull(snapshot);
        assertEquals("tar.gz", snapshot.get("format"));
        assertEquals("source-snapshot.tar.gz", snapshot.get("fileName"));
        assertEquals("source-snapshot.tar.gz", snapshot.get("relativePath"));
        assertTrue((Long) snapshot.get("sizeBytes") > 0);
        assertEquals(64, ((String) snapshot.get("checksumSha256")).length());
        assertTrue(finishedEvent.artifacts().isEmpty());
        assertNull(finishedEvent.logs());

        assertEquals(1, logPublisher.events.size());
        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertEquals(EventType.JOB_LOG, logEvent.eventType());
        assertNotNull(logEvent.logs());
        assertTrue(logEvent.logs().contains("Git checkout shallow clone завершен"));
        assertTrue(logEvent.logs().contains("Source snapshot tar.gz подготовлен"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertEquals("vcs", json.get("jobType").textValue());
        assertEquals("vcs/git", json.get("templatePath").textValue());
        assertEquals("JOB_FINISHED", json.get("eventType").textValue());
        assertEquals("SUCCESS", json.get("status").textValue());
        assertEquals(
                expectedCommit, json.get("additionalData").get("commitHash").textValue());
        assertEquals(
                "tar.gz",
                json.get("additionalData").get("snapshot").get("format").textValue());
        assertEquals(
                "source-snapshot.tar.gz",
                json.get("additionalData").get("snapshot").get("fileName").textValue());
        assertTrue(json.get("additionalData").get("snapshot").get("sizeBytes").longValue() > 0);
        assertTrue(json.get("logs").isNull());
        assertFalse(json.has("event_type"));
    }

    @Test
    void handleGitJobRedactsRepositoryCredentialsFromLogsAndFinishedEvent() throws Exception {
        String rawRepositoryUrl = "https://user:secret-token@example.test/repo.git";
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        CapturingLogPublisher logPublisher = new CapturingLogPublisher();
        ExecutorJobHandler handler = new ExecutorJobHandler(
                new LocalWorkspaceManager(tempDir.resolve("workspaces")),
                eventPublisher,
                logPublisher,
                new SecretRedactor(),
                "vcs-test-worker-1");
        SequenceProcessRunner processRunner = new SequenceProcessRunner(List.of(
                processResult(0, "", "Cloning from https://user:secret-token@example.test/repo.git\n"),
                processResult(0, "0123456789abcdef0123456789abcdef01234567\n", ""),
                processResult(0, "", "")));
        VcsGitCheckoutJob job =
                new VcsGitCheckoutJob(new GitCheckoutRunner(processRunner), new SourceSnapshotArchiver(processRunner));

        ExecutorEventMessage finishedEvent = handler.handle(vcsJob(rawRepositoryUrl), job);

        ExecutorEventMessage logEvent = logPublisher.events.getFirst();
        assertFalse(logEvent.logs().contains("secret-token"));
        assertTrue(logEvent.logs().contains("https://[REDACTED]@example.test/repo.git"));

        JsonNode json = objectMapper().readTree(objectMapper().writeValueAsString(finishedEvent));
        assertTrue(json.get("logs").isNull());
        assertEquals(
                "https://[REDACTED]@example.test/repo.git",
                json.get("additionalData")
                        .get("repository")
                        .get("repositoryUrl")
                        .textValue());
        assertFalse(objectMapper().writeValueAsString(json).contains("secret-token"));
    }

    private JobMessage vcsJob(Path repository) {
        return vcsJob(repository.toUri().toString());
    }

    private JobMessage vcsJob(String repositoryUrl) {
        return new JobMessage(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                UUID.fromString("00000000-0000-0000-0000-000000000103"),
                UUID.fromString("00000000-0000-0000-0000-000000000104"),
                UUID.fromString("00000000-0000-0000-0000-000000000105"),
                UUID.fromString("00000000-0000-0000-0000-000000000106"),
                JOB_EXECUTION_ID,
                JobType.VCS,
                "vcs/git",
                1,
                1,
                30,
                ResourceLimits.empty(),
                new WorkspacePolicy("always", false),
                safeSandboxPolicy(),
                Map.of(),
                Map.of("vcs_type", "git", "repository_url", repositoryUrl, "checkout_depth", 1),
                Map.of("refs", List.of()),
                Instant.parse("2026-05-30T09:00:00Z"));
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

    private SandboxPolicy safeSandboxPolicy() {
        return new SandboxPolicy(
                false,
                false,
                true,
                false,
                true,
                List.of(),
                List.of("ALL"),
                "RuntimeDefault",
                "none",
                List.of(),
                List.of(),
                false,
                Map.of());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
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

    private static final class CapturingEventPublisher implements ExecutorEventPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class CapturingLogPublisher implements ExecutorLogPublisher {

        private final List<ExecutorEventMessage> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> publish(ExecutorEventMessage logEvent) {
            events.add(logEvent);
            return CompletableFuture.completedFuture(null);
        }
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
            prepareExpectedSideEffects(request.command());
            return results.get(index++);
        }

        private void prepareExpectedSideEffects(List<String> command) {
            try {
                if (command.size() >= 2 && "git".equals(command.getFirst()) && "clone".equals(command.get(1))) {
                    Path checkoutPath = Path.of(command.getLast());
                    Files.createDirectories(checkoutPath);
                    Files.writeString(checkoutPath.resolve("README.md"), "fake checkout\n");
                }
                if (command.size() >= 3 && "tar".equals(command.getFirst()) && "-czf".equals(command.get(1))) {
                    Files.writeString(Path.of(command.get(2)), "fake archive\n");
                }
            } catch (java.io.IOException exception) {
                throw new AssertionError("Не удалось подготовить side effect тестового процесса", exception);
            }
        }
    }
}
