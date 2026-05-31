package ru.diplom.cicd.build.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class ExpectedArtifactResolverTest {

    @TempDir
    private Path tempDir;

    private final ExpectedArtifactResolver resolver = new ExpectedArtifactResolver();

    @Test
    void resolveFindsFilesByRelativeGlobPattern() throws Exception {
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("target/app.jar"), "jar");
        Files.writeString(tempDir.resolve("target/app.txt"), "txt");

        List<ExpectedArtifact> artifacts = resolver.resolve(tempDir, List.of("target/*.jar"));

        assertEquals(1, artifacts.size());
        assertEquals("target/*.jar", artifacts.getFirst().pattern());
        assertEquals("target/app.jar", artifacts.getFirst().relativePathText());
        assertEquals(3, artifacts.getFirst().sizeBytes());
    }

    @Test
    void resolveReturnsEmptyListWhenPatternDoesNotMatchFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("target/app.txt"), "txt");

        List<ExpectedArtifact> artifacts = resolver.resolve(tempDir, List.of("target/*.jar"));

        assertTrue(artifacts.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../*.jar", "/tmp/*.jar", "target/["})
    void resolveRejectsInvalidPattern(String pattern) {
        List<String> patterns = List.of(pattern);

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> resolver.resolve(tempDir, patterns));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
    }
}
