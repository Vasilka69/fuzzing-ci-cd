package ru.diplom.cicd.executor.core.process;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Результат запуска процесса без маппинга в доменные статусы job.
 */
public record ProcessExecutionResult(
        int exitCode,
        boolean timedOut,
        boolean killedAfterGracePeriod,
        Duration duration,
        List<ProcessOutputChunk> outputChunks,
        Set<ProcessStreamType> truncatedStreams) {

    public ProcessExecutionResult(
            int exitCode,
            boolean timedOut,
            boolean killedAfterGracePeriod,
            Duration duration,
            List<ProcessOutputChunk> outputChunks) {
        this(exitCode, timedOut, killedAfterGracePeriod, duration, outputChunks, Set.of());
    }

    public ProcessExecutionResult {
        Objects.requireNonNull(duration, "duration");
        outputChunks = outputChunks == null
                ? List.of()
                : outputChunks.stream()
                        .sorted(Comparator.comparingLong(ProcessOutputChunk::sequence))
                        .toList();
        truncatedStreams = truncatedStreams == null ? Set.of() : Set.copyOf(truncatedStreams);
    }

    public List<ProcessOutputChunk> stdoutChunks() {
        return chunks(ProcessStreamType.STDOUT);
    }

    public List<ProcessOutputChunk> stderrChunks() {
        return chunks(ProcessStreamType.STDERR);
    }

    public String stdoutText(Charset charset) {
        return text(ProcessStreamType.STDOUT, charset);
    }

    public String stderrText(Charset charset) {
        return text(ProcessStreamType.STDERR, charset);
    }

    public boolean stdoutTruncated() {
        return truncatedStreams.contains(ProcessStreamType.STDOUT);
    }

    public boolean stderrTruncated() {
        return truncatedStreams.contains(ProcessStreamType.STDERR);
    }

    private List<ProcessOutputChunk> chunks(ProcessStreamType stream) {
        return outputChunks.stream()
                .filter(chunk -> chunk.stream() == stream)
                .sorted(Comparator.comparingLong(ProcessOutputChunk::sequence))
                .toList();
    }

    private String text(ProcessStreamType stream, Charset charset) {
        Objects.requireNonNull(charset, "charset");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        chunks(stream).forEach(chunk -> bytes.writeBytes(chunk.data()));
        return new String(bytes.toByteArray(), charset);
    }
}
