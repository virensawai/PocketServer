package com.example.pocketserver.core.config;

/**
 * Centralized, configurable resource limits and timeouts for PocketServer.
 * Values can be configured without altering application core logic.
 */
public final class LimitsConfig {

    private LimitsConfig() {
        // Utility / Configuration class
    }

    /** Maximum recommended static project size: 100 MB */
    public static final long MAX_PROJECT_SIZE_BYTES = 100L * 1024L * 1024L;

    /** Maximum size for an individual static file: 50 MB */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

    /** Maximum number of files to index or traverse: 10,000 */
    public static final int MAX_FILE_COUNT = 10_000;

    /** Maximum concurrent request streams processed on device */
    public static final int MAX_CONCURRENT_REQUESTS = 20;

    /** Maximum allowed HTTP request header block size: 8 KB */
    public static final int MAX_REQUEST_HEADER_SIZE = 8 * 1024;

    /** Maximum URL length: 2048 characters */
    public static final int MAX_URL_LENGTH = 2048;

    /** Maximum response stream duration before timing out: 30 seconds */
    public static final long MAX_RESPONSE_DURATION_MS = 30_000L;

    /** WebSocket binary frame chunk size: 32 KB */
    public static final int DEFAULT_BUFFER_CHUNK_SIZE = 32 * 1024;

    /** Maximum WebSocket frame size: 64 KB */
    public static final int MAX_WEBSOCKET_FRAME_SIZE = 64 * 1024;

    /** High-water mark for WebSocket outbound queue backpressure: 256 KB */
    public static final long WEBSOCKET_QUEUE_HIGH_WATER_MARK = 256L * 1024L;

    /** Low-water mark for WebSocket outbound queue backpressure: 64 KB */
    public static final long WEBSOCKET_QUEUE_LOW_WATER_MARK = 64L * 1024L;

    /** WebSocket heartbeat interval in seconds: 15s */
    public static final int WEBSOCKET_HEARTBEAT_INTERVAL_SECONDS = 15;

    /** Default cloud relay gateway endpoint (pointing to local PC test relay) */
    public static final String DEFAULT_RELAY_URL = "ws://localhost:8088/tunnel";

    /** Default control plane REST API base URL */
    public static final String DEFAULT_API_BASE_URL = "https://api.pocketserver.dev";

    /** Default LRU cache size for resolved SAF DocumentFile URIs */
    public static final int SAF_LRU_CACHE_CAPACITY = 1000;

    /** Negative cache TTL for non-existent files (milliseconds): 30 seconds */
    public static final long NEGATIVE_CACHE_TTL_MS = 30_000L;

    /** Default local server port */
    public static final int DEFAULT_LOCAL_PORT = 8080;
}
