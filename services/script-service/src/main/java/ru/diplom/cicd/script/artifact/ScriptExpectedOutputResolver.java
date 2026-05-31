package ru.diplom.cicd.script.artifact;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.diplom.cicd.contracts.error.ErrorType;
import ru.diplom.cicd.contracts.event.ExecutionStatus;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;

@Component
public final class ScriptExpectedOutputResolver {

    public List<ScriptOutputArtifact> resolve(Path workingDirectory, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }
        Path root = workingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw ExecutorJobException.validation("working_directory для поиска expected_outputs не существует");
        }
        List<String> safePatterns = patterns.stream().map(this::validatePattern).toList();
        List<Path> files = regularFiles(root);
        return safePatterns.stream()
                .flatMap(pattern -> matchedOutputs(root, files, pattern).stream())
                .sorted(Comparator.comparing(ScriptOutputArtifact::relativePathText)
                        .thenComparing(ScriptOutputArtifact::pattern))
                .toList();
    }

    private String validatePattern(String pattern) {
        String normalizedPattern = StringUtils.trimToEmpty(pattern).replace('\\', '/');
        if (StringUtils.isBlank(normalizedPattern)) {
            throw ExecutorJobException.validation("expected_outputs не должен содержать пустые glob-паттерны");
        }
        if (normalizedPattern.startsWith("/") || isWindowsAbsolutePath(normalizedPattern)) {
            throw ExecutorJobException.validation(
                    "expected_outputs должен содержать только относительные glob-паттерны");
        }
        try {
            Path path = Path.of(normalizedPattern);
            if (path.isAbsolute() || containsParentTraversal(normalizedPattern)) {
                throw ExecutorJobException.validation("expected_outputs не должен выходить за пределы workspace");
            }
        } catch (InvalidPathException exception) {
            throw ExecutorJobException.validation("expected_outputs содержит некорректный glob-паттерн");
        }
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        } catch (IllegalArgumentException exception) {
            throw ExecutorJobException.validation("expected_outputs содержит некорректный glob-паттерн");
        }
        return normalizedPattern;
    }

    private boolean isWindowsAbsolutePath(String pattern) {
        return pattern.length() >= 3
                && Character.isLetter(pattern.charAt(0))
                && pattern.charAt(1) == ':'
                && pattern.charAt(2) == '/';
    }

    private boolean containsParentTraversal(String pattern) {
        for (String segment : pattern.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> regularFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "script.expected-outputs.scan",
                    "Не удалось просканировать expected_outputs в workspace",
                    exception.getMessage(),
                    java.util.Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }

    private List<ScriptOutputArtifact> matchedOutputs(Path root, List<Path> files, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        return files.stream()
                .map(root::relativize)
                .filter(matcher::matches)
                .map(relativePath -> new ScriptOutputArtifact(pattern, relativePath, size(root.resolve(relativePath))))
                .toList();
    }

    private long size(Path artifactPath) {
        try {
            return Files.size(artifactPath);
        } catch (IOException exception) {
            throw new ExecutorJobException(
                    ErrorType.INFRASTRUCTURE_ERROR,
                    "script.expected-outputs.size",
                    "Не удалось прочитать размер expected output",
                    exception.getMessage(),
                    java.util.Map.of("exceptionClass", exception.getClass().getName()),
                    ExecutionStatus.FAILED);
        }
    }
}
