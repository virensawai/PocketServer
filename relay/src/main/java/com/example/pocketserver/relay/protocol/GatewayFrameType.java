package com.example.pocketserver.relay.protocol;

/**
 * Enumeration of reverse tunnel protocol frame types matching Android TunnelClient specification.
 */
public enum GatewayFrameType {
    REGISTER,
    REGISTER_OK,
    REQUEST_START,
    REQUEST_BODY,
    RESPONSE_START,
    RESPONSE_BODY,
    STREAM_END,
    REQUEST_CANCEL,
    PING,
    PONG,
    ERROR
}
