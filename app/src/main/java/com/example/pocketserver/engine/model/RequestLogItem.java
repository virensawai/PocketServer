package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Immutable telemetry model representing a single HTTP request processed by the server.
 * Displayed in the real-time request log inspector on the Android Dashboard.
 */
public class RequestLogItem implements Serializable {

    private final String requestId;
    private final String method;
    private final String path;
    private final int statusCode;
    private final long bytes;
    private final long durationMs;
    private final long timestamp;

    public RequestLogItem(
            @NonNull String requestId,
            @NonNull String method,
            @NonNull String path,
            int statusCode,
            long bytes,
            long durationMs,
            long timestamp) {
        this.requestId = requestId;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.bytes = bytes;
        this.durationMs = durationMs;
        this.timestamp = timestamp;
    }

    @NonNull
    public String getRequestId() {
        return requestId;
    }

    @NonNull
    public String getMethod() {
        return method;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getBytes() {
        return bytes;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    @NonNull
    public String getFormattedBytes() {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
