package com.example.pocketserver.control.model;

/**
 * Model representing a project deployment state in the Control Plane.
 */
public class Deployment {

    private final String id;
    private final String projectId;
    private final String deviceId;
    private final String userId;
    private final int version;
    private final String hostingMode; // LOCAL | PUBLIC
    private final String publicHostname;
    private volatile String status; // STOPPED, STARTING, CONNECTING, LIVE, NETWORK_LOST, RECONNECTING, FAILED
    private final boolean spaFallbackEnabled;
    private volatile long dataTransferredBytes;
    private volatile long requestCount;
    private final long createdAt;
    private volatile long stoppedAt;

    public Deployment(
            String id,
            String projectId,
            String deviceId,
            String userId,
            int version,
            String hostingMode,
            String publicHostname,
            String status,
            boolean spaFallbackEnabled,
            long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.deviceId = deviceId;
        this.userId = userId;
        this.version = version;
        this.hostingMode = hostingMode;
        this.publicHostname = publicHostname;
        this.status = status;
        this.spaFallbackEnabled = spaFallbackEnabled;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getUserId() {
        return userId;
    }

    public int getVersion() {
        return version;
    }

    public String getHostingMode() {
        return hostingMode;
    }

    public String getPublicHostname() {
        return publicHostname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSpaFallbackEnabled() {
        return spaFallbackEnabled;
    }

    public long getDataTransferredBytes() {
        return dataTransferredBytes;
    }

    public void setDataTransferredBytes(long dataTransferredBytes) {
        this.dataTransferredBytes = dataTransferredBytes;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(long stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
