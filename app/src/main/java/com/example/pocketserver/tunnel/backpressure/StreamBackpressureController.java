package com.example.pocketserver.tunnel.backpressure;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.config.LimitsConfig;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flow control regulator enforcing bounded memory consumption on file-to-socket streams.
 * When the downstream socket buffer exceeds the high-water mark (e.g. 256 KB), reading
 * from storage is throttled until the queue drains below the low-water mark (e.g. 64 KB).
 */
public class StreamBackpressureController {

    private final long highWaterMarkBytes;
    private final long lowWaterMarkBytes;
    private final long pollIntervalMs;

    public StreamBackpressureController() {
        this(
                LimitsConfig.WEBSOCKET_QUEUE_HIGH_WATER_MARK,
                LimitsConfig.WEBSOCKET_QUEUE_LOW_WATER_MARK,
                15L
        );
    }

    public StreamBackpressureController(long highWaterMarkBytes, long lowWaterMarkBytes, long pollIntervalMs) {
        this.highWaterMarkBytes = highWaterMarkBytes;
        this.lowWaterMarkBytes = lowWaterMarkBytes;
        this.pollIntervalMs = Math.max(1L, pollIntervalMs);
    }

    public long getHighWaterMarkBytes() {
        return highWaterMarkBytes;
    }

    public long getLowWaterMarkBytes() {
        return lowWaterMarkBytes;
    }

    /**
     * Determines whether outbound socket buffering exceeds the congestion threshold.
     */
    public boolean isCongested(long currentQueueBytes) {
        return currentQueueBytes >= highWaterMarkBytes;
    }

    /**
     * Blocks the calling reader thread until the outbound queue drains below the low-water mark,
     * the stream is cancelled, or the timeout expires.
     *
     * @param provider         supplier of current queue size
     * @param cancellationFlag flag indicating if the stream has been aborted
     * @param maxWaitMs        maximum time to wait in milliseconds
     * @return true if queue successfully drained; false if timed out or cancelled
     */
    public boolean awaitDrain(
            @NonNull QueueSizeProvider provider,
            @Nullable AtomicBoolean cancellationFlag,
            long maxWaitMs) throws InterruptedException {

        long startTime = System.currentTimeMillis();

        while (true) {
            if (cancellationFlag != null && cancellationFlag.get()) {
                return false;
            }

            long currentQueue = provider.getQueueSize();
            if (currentQueue <= lowWaterMarkBytes) {
                return true;
            }

            if (System.currentTimeMillis() - startTime >= maxWaitMs) {
                return false;
            }

            Thread.sleep(pollIntervalMs);
        }
    }
}
