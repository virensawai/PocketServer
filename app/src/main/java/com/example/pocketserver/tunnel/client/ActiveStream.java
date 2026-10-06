package com.example.pocketserver.tunnel.client;

import androidx.annotation.NonNull;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks an active, multiplexed file-streaming response being served over the tunnel.
 * Maintains cancellation state and references to open I/O resources.
 */
public class ActiveStream {

    private final String requestId;
    private final UUID requestUuid;
    private final InputStream inputStream;
    private final byte[] buffer;
    private final long startTimeMs;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public ActiveStream(
            @NonNull String requestId,
            @NonNull UUID requestUuid,
            @NonNull InputStream inputStream,
            int bufferSize) {
        this.requestId = requestId;
        this.requestUuid = requestUuid;
        this.inputStream = inputStream;
        this.buffer = new byte[bufferSize];
        this.startTimeMs = System.currentTimeMillis();
    }

    @NonNull
    public String getRequestId() {
        return requestId;
    }

    @NonNull
    public UUID getRequestUuid() {
        return requestUuid;
    }

    @NonNull
    public InputStream getInputStream() {
        return inputStream;
    }

    @NonNull
    public byte[] getBuffer() {
        return buffer;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    @NonNull
    public AtomicBoolean getCancellationFlag() {
        return isCancelled;
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    /**
     * Immediately aborts the streaming task and closes underlying I/O resources.
     */
    public void cancel() {
        if (isCancelled.compareAndSet(false, true)) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
        }
    }
}
