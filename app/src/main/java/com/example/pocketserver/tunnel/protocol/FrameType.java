package com.example.pocketserver.tunnel.protocol;

/**
 * Enumeration of control and data frame types defined by the
 * multiplexed reverse tunnel protocol.
 */
public enum FrameType {
    /**
     * Handshake frame: Android client registering deployment on gateway.
     */
    REGISTER,

    /**
     * Handshake response: Gateway confirms deployment and assigns public URL.
     */
    REGISTER_OK,

    /**
     * Inbound visitor HTTP request start metadata (headers, method, path).
     */
    REQUEST_START,

    /**
     * Inbound request payload chunk (raw binary).
     */
    REQUEST_BODY,

    /**
     * Outbound HTTP response start metadata (status, headers, content-length).
     */
    RESPONSE_START,

    /**
     * Outbound response body chunk (raw binary with 16-byte UUID header).
     */
    RESPONSE_BODY,

    /**
     * Completion signal for an outbound response stream.
     */
    STREAM_END,

    /**
     * Cancellation signal sent when visitor disconnects prematurely.
     */
    REQUEST_CANCEL,

    /**
     * Control heartbeat ping.
     */
    PING,

    /**
     * Control heartbeat pong.
     */
    PONG,

    /**
     * Control error frame (e.g. rate limit, auth failure, invalid frame).
     */
    ERROR
}
