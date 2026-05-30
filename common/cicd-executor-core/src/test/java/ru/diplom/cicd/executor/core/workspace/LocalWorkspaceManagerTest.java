package ru.diplom.cicd.executor.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.diplom.cicd.contracts.job.WorkspacePolicy;

class LocalWorkspaceManagerTest {

    private static final UUID JOB_EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @TempDir
    private Path tempDir;

    @Test
    void createMakesWorkspaceDirectoryForJobExecutionId() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);

        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", false));

        assertEquals(JOB_EXECUTION_ID, workspace.jobExecutionId());
        assertEquals(
                tempDir.resolve(JOB_EXECUTION_ID.toString()).toAbsolutePath().normalize(), workspace.root());
        assertTrue(Files.isDirectory(workspace.root()));
    }

    @Test
    void createUsesCleanupAlwaysWhenPolicyIsMissing() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);

        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, null);

        assertEquals(new WorkspacePolicy("always", false), workspace.policy());
    }

    @Test
    void resolveReturnsNormalizedPathInsideWorkspace() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", false));

        Path resolvedPath = manager.resolve(workspace, "build/../reports/result.json");

        assertEquals(workspace.root().resolve("reports/result.json"), resolvedPath);
    }

    @Test
    void resolveRejectsPathTraversalOutsideWorkspace() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", false));

        WorkspaceException error =
                assertThrows(WorkspaceException.class, () -> manager.resolve(workspace, "../outside.txt"));

        assertEquals("Путь workspace выходит за пределы рабочей директории: ../outside.txt", error.getMessage());
    }

    @Test
    void resolveRejectsAbsolutePath() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", false));

        WorkspaceException error =
                assertThrows(WorkspaceException.class, () -> manager.resolve(workspace, "/tmp/outside.txt"));

        assertEquals("Абсолютный путь в workspace запрещен: /tmp/outside.txt", error.getMessage());
    }

    @Test
    void cleanupAlwaysDeletesWorkspaceRecursively() throws IOException {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", false));
        Path nestedFile = manager.resolve(workspace, "reports/result.json");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(nestedFile, "ok");

        boolean deleted = manager.cleanup(workspace, false);

        assertTrue(deleted);
        assertFalse(Files.exists(workspace.root()));
    }

    @Test
    void preserveOnFailureKeepsWorkspaceWhenJobFailed() throws IOException {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("always", true));
        Path logFile = manager.resolve(workspace, "logs/job.log");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "failed");

        boolean deleted = manager.cleanup(workspace, true);

        assertFalse(deleted);
        assertTrue(Files.exists(logFile));
    }

    @Test
    void cleanupNeverKeepsWorkspace() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("never", false));

        boolean deleted = manager.cleanup(workspace, false);

        assertFalse(deleted);
        assertTrue(Files.isDirectory(workspace.root()));
    }

    @Test
    void cleanupRejectsUnknownPolicy() {
        LocalWorkspaceManager manager = new LocalWorkspaceManager(tempDir);
        WorkspaceHandle workspace = manager.create(JOB_EXECUTION_ID, new WorkspacePolicy("sometimes", false));

        WorkspaceException error = assertThrows(WorkspaceException.class, () -> manager.cleanup(workspace, false));

        assertEquals("Неизвестная политика очистки workspace: sometimes", error.getMessage());
    }
}
