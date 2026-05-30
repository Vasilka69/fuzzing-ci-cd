package ru.diplom.cicd.executor.core.process;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Ограниченный по размеру фрагмент stdout или stderr.
 */
@SuppressWarnings("java:S6218")
public record ProcessOutputChunk(ProcessStreamType stream, long sequence, byte[] data) {

    public ProcessOutputChunk {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(data, "data");
        if (sequence < 0) {
            throw new IllegalArgumentException("Номер chunk-а не может быть отрицательным");
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public int byteSize() {
        return data.length;
    }

    /**
     * Декодирует только текущий chunk. Для точного восстановления много-byte текста лучше
     * склеить {@link #data()} всех chunk-ов одного stream-а и декодировать общий byte array.
     */
    public String text(Charset charset) {
        return new String(data, Objects.requireNonNull(charset, "charset"));
    }
}
