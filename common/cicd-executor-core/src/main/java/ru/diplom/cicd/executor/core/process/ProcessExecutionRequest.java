package ru.diplom.cicd.executor.core.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Описание запуска внешнего процесса. Команда задается только списком аргументов,
 * чтобы executor-ы не собирали shell string из пользовательского ввода.
 */
public record ProcessExecutionRequest(
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        boolean inheritEnvironment,
        Duration timeout,
        Duration gracePeriod,
        int outputChunkBytes,
        ProcessOutputConsumer outputConsumer) {

    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofSeconds(10);
    public static final int DEFAULT_OUTPUT_CHUNK_BYTES = 16 * 1024;

    public ProcessExecutionRequest {
        command = validateCommand(command);
        environment = validateEnvironment(environment);
        requirePositive(timeout, "Timeout процесса должен быть положительным");
        gracePeriod = gracePeriod == null ? DEFAULT_GRACE_PERIOD : gracePeriod;
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("Grace period процесса не может быть отрицательным");
        }
        if (outputChunkBytes < 1) {
            throw new IllegalArgumentException("Размер stdout/stderr chunk-а должен быть положительным");
        }
        outputConsumer = outputConsumer == null ? ProcessOutputConsumer.NOOP : outputConsumer;
    }

    public static Builder builder(List<String> command) {
        return new Builder(command);
    }

    private static List<String> validateCommand(List<String> command) {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Команда процесса не может быть пустой");
        }
        for (String argument : command) {
            if (argument == null) {
                throw new IllegalArgumentException("Аргументы процесса не должны быть null");
            }
        }
        List<String> immutableCommand = List.copyOf(command);
        if (immutableCommand.getFirst().isBlank()) {
            throw new IllegalArgumentException("Executable процесса не может быть пустым");
        }
        return immutableCommand;
    }

    private static Map<String, String> validateEnvironment(Map<String, String> environment) {
        if (environment == null || environment.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        environment.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Имя переменной окружения не может быть пустым");
            }
            if (value == null) {
                throw new IllegalArgumentException("Значение переменной окружения не может быть null");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    private static Duration requirePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return duration;
    }

    public static final class Builder {

        private final List<String> command;
        private Path workingDirectory;
        private final Map<String, String> environment = new LinkedHashMap<>();
        private boolean inheritEnvironment = true;
        private Duration timeout;
        private Duration gracePeriod = DEFAULT_GRACE_PERIOD;
        private int outputChunkBytes = DEFAULT_OUTPUT_CHUNK_BYTES;
        private ProcessOutputConsumer outputConsumer = ProcessOutputConsumer.NOOP;

        private Builder(List<String> command) {
            this.command = command;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment.clear();
            if (environment != null) {
                this.environment.putAll(environment);
            }
            return this;
        }

        public Builder environment(String name, String value) {
            this.environment.put(name, value);
            return this;
        }

        public Builder inheritEnvironment(boolean inheritEnvironment) {
            this.inheritEnvironment = inheritEnvironment;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        public Builder outputChunkBytes(int outputChunkBytes) {
            this.outputChunkBytes = outputChunkBytes;
            return this;
        }

        public Builder outputConsumer(ProcessOutputConsumer outputConsumer) {
            this.outputConsumer = outputConsumer;
            return this;
        }

        public ProcessExecutionRequest build() {
            return new ProcessExecutionRequest(
                    command,
                    workingDirectory,
                    environment,
                    inheritEnvironment,
                    timeout,
                    gracePeriod,
                    outputChunkBytes,
                    outputConsumer);
        }
    }
}
