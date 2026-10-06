package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;
import java.util.Locale;

/**
 * Real-time operational telemetry for an active deployment.
 * Tracks throughput, request volume, concurrency, and uptime.
 */
public class DeploymentTelemetry {

    private final long requestCount;
    private final long bytesTransferred;
    private final int activeStreams;
    private final long startedAtTimestamp;
    private final long lastRequestTimestamp;

    public DeploymentTelemetry() {
        this(0, 0, 0, 0, 0);
    }

    public DeploymentTelemetry(
            long requestCount,
            long bytesTransferred,
            int activeStreams,
            long startedAtTimestamp,
            long lastRequestTimestamp) {
        this.requestCount = requestCount;
        this.bytesTransferred = bytesTransferred;
        this.activeStreams = Math.max(0, activeStreams);
        this.startedAtTimestamp = startedAtTimestamp;
        this.lastRequestTimestamp = lastRequestTimestamp;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public int getActiveStreams() {
        return activeStreams;
    }

    public long getStartedAtTimestamp() {
        return startedAtTimestamp;
    }

    public long getLastRequestTimestamp() {
        return lastRequestTimestamp;
    }

    /**
     * Calculates total uptime in milliseconds since start.
     */
    public long getUptimeMs() {
        if (startedAtTimestamp <= 0) {
            return 0;
        }
        return Math.max(0, System.currentTimeMillis() - startedAtTimestamp);
    }

    /**
     * Formats uptime into a standard "HH:mm:ss" string.
     */
    @NonNull
    public String getFormattedUptime() {
        long totalSeconds = getUptimeMs() / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Formats bytes transferred into a human-readable string (e.g. "4.2 MB").
     */
    @NonNull
    public String getFormattedBytes() {
        return formatBytes(bytesTransferred);
    }

    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, 4); // up to TB
        char unit = "KMGT".charAt(exp - 1);
        double value = bytes / Math.pow(1024, exp);
        return String.format(Locale.US, "%.1f %cB", value, unit);
    }

    @NonNull
    public DeploymentTelemetry withAddedRequest(long additionalBytes) {
        return new DeploymentTelemetry(
                this.requestCount + 1,
                this.bytesTransferred + Math.max(0, additionalBytes),
                this.activeStreams,
                this.startedAtTimestamp <= 0 ? System.currentTimeMillis() : this.startedAtTimestamp,
                System.currentTimeMillis()
        );
    }

    @NonNull
    public DeploymentTelemetry withActiveStreams(int newActiveStreams) {
        return new DeploymentTelemetry(
                this.requestCount,
                this.bytesTransferred,
                newActiveStreams,
                this.startedAtTimestamp,
                this.lastRequestTimestamp
        );
    }

    @NonNull
    public DeploymentTelemetry withAddedBytes(long additionalBytes) {
        return new DeploymentTelemetry(
                this.requestCount,
                this.bytesTransferred + Math.max(0, additionalBytes),
                this.activeStreams,
                this.startedAtTimestamp,
                this.lastRequestTimestamp
        );
    }
}
