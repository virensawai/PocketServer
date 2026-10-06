package com.example.pocketserver.control.model;

/**
 * Ephemeral, short-lived tunnel credential (TTL 24 hours) issued by the Control Plane.
 */
public class TunnelToken {

    private final String token;
    private final String deploymentId;
    private final String deviceId;
    private final long expiresAt;
    private final long createdAt;

    public TunnelToken(String token, String deploymentId, String deviceId, long expiresAt, long createdAt) {
        this.token = token;
        this.deploymentId = deploymentId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getToken() {
        return token;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
