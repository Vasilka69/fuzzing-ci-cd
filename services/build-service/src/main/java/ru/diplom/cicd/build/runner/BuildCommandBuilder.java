package ru.diplom.cicd.build.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

/**
 * Собирает команду build-инструмента только из whitelist entrypoint-ов.
 *
 * <p>Это главный предохранитель build-service от запуска произвольного shell/script имени из job params:
 * пользовательские аргументы передаются отдельными argv-элементами, а executable должен совпасть
 * с разрешенным entrypoint для выбранного build tool.
 */
public final class BuildCommandBuilder {

    private static final Map<BuildTool, Set<String>> ALLOWED_ENTRYPOINTS = Map.of(
            BuildTool.MAVEN, Set.of("mvn", "./mvnw"),
            BuildTool.GRADLE, Set.of("gradle", "./gradlew"));

    public List<String> command(BuildParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        validateEntrypoint(parameters);
        List<String> command = new ArrayList<>();
        command.add(parameters.entrypoint());
        command.addAll(parameters.args());
        return List.copyOf(command);
    }

    private void validateEntrypoint(BuildParameters parameters) {
        Set<String> allowedEntrypoints = ALLOWED_ENTRYPOINTS.getOrDefault(parameters.buildTool(), Set.of());
        if (!allowedEntrypoints.contains(parameters.entrypoint())) {
            throw ExecutorJobException.validation("entrypoint запрещен для "
                    + parameters.buildTool().wireValue()
                    + ": разрешены "
                    + String.join(", ", allowedEntrypoints));
        }
    }
}
