package com.example.pocketserver.tunnel.backpressure;

/**
 * Interface providing the current downstream outbound queue byte size.
 * Allows decoupling OkHttp WebSocket queue inspection from backpressure logic.
 */
public interface QueueSizeProvider {

    /**
     * @return current number of bytes queued in memory waiting for socket transmission
     */
    long getQueueSize();
}
