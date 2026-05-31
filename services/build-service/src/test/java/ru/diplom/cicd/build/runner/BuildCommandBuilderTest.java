package ru.diplom.cicd.build.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

class BuildCommandBuilderTest {

    private static final String SOURCE_SNAPSHOT_URI = "storage://source-snapshots/job-1/source-snapshot.tar.gz";

    private final BuildCommandBuilder commandBuilder = new BuildCommandBuilder();

    @Test
    void commandAllowsMavenWrapperEntrypoint() {
        BuildParameters parameters = new BuildParameters(
                BuildTool.MAVEN, SOURCE_SNAPSHOT_URI, Path.of("."), "./mvnw", List.of("-q", "test"), Map.of());

        List<String> command = commandBuilder.command(parameters);

        assertEquals(List.of("./mvnw", "-q", "test"), command);
    }

    @Test
    void commandRejectsArbitraryEntrypoint() {
        BuildParameters parameters = new BuildParameters(
                BuildTool.GRADLE, SOURCE_SNAPSHOT_URI, Path.of("."), "bash", List.of("-lc", "gradle build"), Map.of());

        ExecutorJobException exception =
                assertThrows(ExecutorJobException.class, () -> commandBuilder.command(parameters));

        assertEquals(ErrorType.VALIDATION_ERROR, exception.errorType());
        assertEquals("executor.job.validation", exception.code());
    }
}
