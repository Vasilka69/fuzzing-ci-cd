package ru.diplom.cicd.executor.core.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

/**
 * Process runner на Apache Commons Exec. Он не принимает shell string: каждый аргумент
 * передается отдельно, а timeout реализован в две фазы — graceful destroy и forced destroy
 * после grace period.
 */
public final class LocalProcessRunner implements ProcessRunner {

    private static final Duration FORCED_EXIT_WAIT = Duration.ofSeconds(5);
    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

    @Override
    public ProcessExecutionResult run(ProcessExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        validateWorkingDirectory(request.workingDirectory());

        long startedAtNanos = System.nanoTime();
        List<ProcessOutputChunk> chunks = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<RuntimeException> outputFailure = new AtomicReference<>();
        AtomicLong sequence = new AtomicLong();
        Object emitLock = new Object();
        ProcessOutputConsumer outputConsumer = chunk -> {
            chunks.add(chunk);
            request.outputConsumer().accept(chunk);
        };

        PumpStreamHandler streamHandler = new PumpStreamHandler(
                new ChunkedProcessOutputStream(
                        ProcessStreamType.STDOUT,
                        request.outputChunkBytes(),
                        sequence,
                        emitLock,
                        outputConsumer,
                        outputFailure),
                new ChunkedProcessOutputStream(
                        ProcessStreamType.STDERR,
                        request.outputChunkBytes(),
                        sequence,
                        emitLock,
                        outputConsumer,
                        outputFailure));
        streamHandler.setStopTimeout(request.gracePeriod().plus(FORCED_EXIT_WAIT));

        CapturingProcessDestroyer processDestroyer = new CapturingProcessDestroyer();
        ProcessResultHandler resultHandler = new ProcessResultHandler();
        DefaultExecutor executor = executor(request, streamHandler, processDestroyer);

        try {
            executor.execute(commandLine(request.command()), environment(request), resultHandler);
            ProcessExit processExit = resultHandler.await(request.timeout());
            rethrowOutputFailure(outputFailure);
            return result(processExit.exitCode(), false, false, startedAtNanos, chunks);
        } catch (TimeoutException exception) {
            boolean killedAfterGracePeriod = terminateAfterTimeout(processDestroyer.process(), request.gracePeriod());
            ProcessExit processExit = awaitAfterTermination(resultHandler, processDestroyer.process());
            rethrowOutputFailure(outputFailure);
            return result(processExit.exitCode(), true, killedAfterGracePeriod, startedAtNanos, chunks);
        } catch (IOException exception) {
            throw new ProcessRunnerException("Не удалось запустить дочерний процесс executor-а", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminateAfterTimeout(processDestroyer.process(), Duration.ZERO);
            throw new ProcessRunnerException("Ожидание дочернего процесса executor-а было прервано", exception);
        }
    }

    private DefaultExecutor executor(
            ProcessExecutionRequest request, PumpStreamHandler streamHandler, ProcessDestroyer processDestroyer) {
        DefaultExecutor executor = DefaultExecutor.builder()
                .setWorkingDirectory(request.workingDirectory())
                .setExecuteStreamHandler(streamHandler)
                .setThreadFactory(runnable -> {
                    Thread thread = new Thread(runnable, "cicd-process-runner-" + THREAD_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                })
                .get();
        executor.setExitValues(null);
        executor.setProcessDestroyer(processDestroyer);
        return executor;
    }

    @SuppressWarnings("java:S2445")
    private ProcessExecutionResult result(
            int exitCode,
            boolean timedOut,
            boolean killedAfterGracePeriod,
            long startedAtNanos,
            List<ProcessOutputChunk> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        List<ProcessOutputChunk> snapshot;
        synchronized (chunks) {
            snapshot = List.copyOf(chunks);
        }
        return new ProcessExecutionResult(
                exitCode,
                timedOut,
                killedAfterGracePeriod,
                Duration.ofNanos(System.nanoTime() - startedAtNanos),
                snapshot);
    }

    private CommandLine commandLine(List<String> command) {
        CommandLine commandLine = new CommandLine(command.getFirst());
        command.stream().skip(1).forEach(argument -> commandLine.addArgument(argument, false));
        return commandLine;
    }

    private Map<String, String> environment(ProcessExecutionRequest request) {
        Map<String, String> environment = new LinkedHashMap<>();
        if (request.inheritEnvironment()) {
            environment.putAll(System.getenv());
        }
        environment.putAll(request.environment());
        return environment;
    }

    private void validateWorkingDirectory(Path workingDirectory) {
        if (workingDirectory != null && !Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Рабочая директория процесса не существует: " + workingDirectory);
        }
    }

    private boolean terminateAfterTimeout(Process process, Duration gracePeriod) {
        if (process == null || !process.isAlive()) {
            return false;
        }
        destroyProcessTree(process, false);
        if (waitForProcess(process, gracePeriod)) {
            return false;
        }
        destroyProcessTree(process, true);
        waitForProcess(process, FORCED_EXIT_WAIT);
        return true;
    }

    private void destroyProcessTree(Process process, boolean forcibly) {
        List<ProcessHandle> descendants =
                new ArrayList<>(process.toHandle().descendants().toList());
        descendants.reversed().forEach(handle -> destroy(handle, forcibly));
        destroy(process.toHandle(), forcibly);
    }

    private void destroy(ProcessHandle processHandle, boolean forcibly) {
        if (!processHandle.isAlive()) {
            return;
        }
        if (forcibly) {
            processHandle.destroyForcibly();
        } else {
            processHandle.destroy();
        }
    }

    private boolean waitForProcess(Process process, Duration timeout) {
        if (process == null || !process.isAlive()) {
            return true;
        }
        try {
            if (timeout.isZero() || timeout.isNegative()) {
                return !process.isAlive();
            }
            return process.waitFor(timeoutNanos(timeout), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private ProcessExit awaitAfterTermination(ProcessResultHandler resultHandler, Process process) {
        try {
            return resultHandler.await(FORCED_EXIT_WAIT);
        } catch (TimeoutException exception) {
            return new ProcessExit(exitCodeOrInvalid(process));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProcessExit(exitCodeOrInvalid(process));
        }
    }

    private int exitCodeOrInvalid(Process process) {
        return process != null && !process.isAlive() ? process.exitValue() : Executor.INVALID_EXITVALUE;
    }

    private void rethrowOutputFailure(AtomicReference<RuntimeException> outputFailure) {
        RuntimeException exception = outputFailure.get();
        if (exception != null) {
            throw new ProcessRunnerException("Не удалось обработать stdout/stderr дочернего процесса", exception);
        }
    }

    private static final class ChunkedProcessOutputStream extends java.io.OutputStream {

        private final ProcessStreamType stream;
        private final int chunkBytes;
        private final AtomicLong sequence;
        private final Object emitLock;
        private final ProcessOutputConsumer outputConsumer;
        private final AtomicReference<RuntimeException> outputFailure;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private ChunkedProcessOutputStream(
                ProcessStreamType stream,
                int chunkBytes,
                AtomicLong sequence,
                Object emitLock,
                ProcessOutputConsumer outputConsumer,
                AtomicReference<RuntimeException> outputFailure) {
            this.stream = stream;
            this.chunkBytes = chunkBytes;
            this.sequence = sequence;
            this.emitLock = emitLock;
            this.outputConsumer = outputConsumer;
            this.outputFailure = outputFailure;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            buffer.write(value);
            if (buffer.size() >= chunkBytes) {
                emit();
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            int cursor = offset;
            int remaining = length;
            while (remaining > 0) {
                int writable = Math.min(remaining, chunkBytes - buffer.size());
                buffer.write(bytes, cursor, writable);
                cursor += writable;
                remaining -= writable;
                if (buffer.size() >= chunkBytes) {
                    emit();
                }
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            emit();
        }

        @Override
        public synchronized void close() throws IOException {
            emit();
        }

        private void emit() throws IOException {
            if (buffer.size() == 0) {
                return;
            }
            byte[] data = buffer.toByteArray();
            buffer.reset();
            synchronized (emitLock) {
                ProcessOutputChunk chunk = new ProcessOutputChunk(stream, sequence.getAndIncrement(), data);
                try {
                    outputConsumer.accept(chunk);
                } catch (RuntimeException exception) {
                    outputFailure.compareAndSet(null, exception);
                    throw new IOException("Output chunk consumer завершился с ошибкой", exception);
                }
            }
        }
    }

    private static final class CapturingProcessDestroyer implements ProcessDestroyer {

        private final ShutdownHookProcessDestroyer delegate = new ShutdownHookProcessDestroyer();
        private final AtomicReference<Process> process = new AtomicReference<>();

        @Override
        public boolean add(Process process) {
            this.process.set(process);
            return delegate.add(process);
        }

        @Override
        public boolean remove(Process process) {
            return delegate.remove(process);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        private Process process() {
            return process.get();
        }
    }

    private static final class ProcessResultHandler implements ExecuteResultHandler {

        private final CompletableFuture<ProcessExit> future = new CompletableFuture<>();

        @Override
        public void onProcessComplete(int exitValue) {
            future.complete(new ProcessExit(exitValue));
        }

        @Override
        public void onProcessFailed(ExecuteException exception) {
            future.complete(new ProcessExit(exception.getExitValue()));
        }

        private ProcessExit await(Duration timeout) throws InterruptedException, TimeoutException {
            try {
                return future.get(timeoutNanos(timeout), TimeUnit.NANOSECONDS);
            } catch (ExecutionException exception) {
                throw new ProcessRunnerException("Commons Exec вернул неожиданную ошибку выполнения", exception);
            }
        }
    }

    private record ProcessExit(int exitCode) {}
}
