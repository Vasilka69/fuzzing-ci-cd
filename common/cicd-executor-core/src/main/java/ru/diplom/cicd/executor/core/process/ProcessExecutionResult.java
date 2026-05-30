package ru.diplom.cicd.executor.core.process;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Результат запуска процесса без маппинга в доменные статусы job.
 */
public record ProcessExecutionResult(
        int exitCode,
        boolean timedOut,
        boolean killedAfterGracePeriod,
        Duration duration,
        List<ProcessOutputChunk> outputChunks) {

    public ProcessExecutionResult {
        Objects.requireNonNull(duration, "duration");
        outputChunks = outputChunks == null
                ? List.of()
                : outputChunks.stream()
                        .sorted(Comparator.comparingLong(ProcessOutputChunk::sequence))
                        .toList();
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
