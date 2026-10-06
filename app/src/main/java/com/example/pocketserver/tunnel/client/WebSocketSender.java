package com.example.pocketserver.tunnel.client;

import androidx.annotation.NonNull;
import com.example.pocketserver.tunnel.backpressure.QueueSizeProvider;
import okio.ByteString;

/**
 * Common interface for transmitting text and binary frames over the WebSocket connection.
 */
public interface WebSocketSender extends QueueSizeProvider {

    /**
     * Transmits a UTF-8 text message (e.g. JSON control frame).
     *
     * @param text string payload
     * @return true if successfully enqueued
     */
    boolean sendText(@NonNull String text);

    /**
     * Transmits a raw binary frame (e.g. 16-byte UUID header + file chunk).
     *
     * @param bytes binary payload
     * @return true if successfully enqueued
     */
    boolean sendBinary(@NonNull ByteString bytes);

    /**
     * Closes the underlying socket with a protocol code and reason.
     */
    void close(int code, @NonNull String reason);
}
