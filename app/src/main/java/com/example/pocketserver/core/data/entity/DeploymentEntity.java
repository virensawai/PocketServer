package com.example.pocketserver.core.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for persisted deployment records on the Android device.
 */
@Entity(tableName = "deployments")
public class DeploymentEntity {

    @PrimaryKey
    @NonNull
    private String deploymentId;
    private String projectId;
    private String deviceId;
    private int version;
    private String hostingMode; // LOCAL | PUBLIC
    private String publicHostname;
    private String status; // STOPPED, STARTING, CONNECTING, LIVE, NETWORK_LOST, RECONNECTING, FAILED
    private boolean spaFallbackEnabled;
    private long dataTransferredBytes;
    private long requestCount;
    private long createdAt;
    private long stoppedAt;

    public DeploymentEntity(
            @NonNull String deploymentId,
            String projectId,
            String deviceId,
            int version,
            String hostingMode,
            String publicHostname,
            String status,
            boolean spaFallbackEnabled,
            long dataTransferredBytes,
            long requestCount,
            long createdAt,
            long stoppedAt) {
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.deviceId = deviceId;
        this.version = version;
        this.hostingMode = hostingMode;
        this.publicHostname = publicHostname;
        this.status = status;
        this.spaFallbackEnabled = spaFallbackEnabled;
        this.dataTransferredBytes = dataTransferredBytes;
        this.requestCount = requestCount;
        this.createdAt = createdAt;
        this.stoppedAt = stoppedAt;
    }

    @NonNull
    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(@NonNull String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getHostingMode() {
        return hostingMode;
    }

    public void setHostingMode(String hostingMode) {
        this.hostingMode = hostingMode;
    }

    public String getPublicHostname() {
        return publicHostname;
    }

    public void setPublicHostname(String publicHostname) {
        this.publicHostname = publicHostname;
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

    public void setSpaFallbackEnabled(boolean spaFallbackEnabled) {
        this.spaFallbackEnabled = spaFallbackEnabled;
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

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(long stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
