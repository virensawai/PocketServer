package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Encapsulates the complete state, configuration, and telemetry of a project deployment.
 */
public class Deployment {

    private final String deploymentId;
    private final DeploymentConfig config;
    private final DeploymentState state;
    private final String localUrl;
    private final String publicUrl;
    private final DeploymentTelemetry telemetry;
    private final long createdAt;
    private final long stoppedAt;
    private final String errorMessage;

    public Deployment(
            @NonNull String deploymentId,
            @NonNull DeploymentConfig config,
            @NonNull DeploymentState state,
            @Nullable String localUrl,
            @Nullable String publicUrl,
            @NonNull DeploymentTelemetry telemetry,
            long createdAt,
            long stoppedAt,
            @Nullable String errorMessage) {
        this.deploymentId = deploymentId;
        this.config = config;
        this.state = state;
        this.localUrl = localUrl;
        this.publicUrl = publicUrl;
        this.telemetry = telemetry;
        this.createdAt = createdAt;
        this.stoppedAt = stoppedAt;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public String getDeploymentId() {
        return deploymentId;
    }

    @NonNull
    public DeploymentConfig getConfig() {
        return config;
    }

    @NonNull
    public DeploymentState getState() {
        return state;
    }

    @Nullable
    public String getLocalUrl() {
        return localUrl;
    }

    @Nullable
    public String getPublicUrl() {
        return publicUrl;
    }

    /**
     * Resolves the primary user-facing display URL based on hosting mode.
     */
    @Nullable
    public String getDisplayUrl() {
        if (config.getHostingMode() == HostingMode.PUBLIC && publicUrl != null && !publicUrl.isEmpty()) {
            return publicUrl;
        }
        return localUrl;
    }

    @NonNull
    public DeploymentTelemetry getTelemetry() {
        return telemetry;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getStoppedAt() {
        return stoppedAt;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public Deployment withState(@NonNull DeploymentState newState, @Nullable String errorMessage) {
        return new Deployment(
                this.deploymentId,
                this.config,
                newState,
                this.localUrl,
                this.publicUrl,
                this.telemetry,
                this.createdAt,
                newState == DeploymentState.STOPPED ? System.currentTimeMillis() : this.stoppedAt,
                errorMessage
        );
    }

    @NonNull
    public Deployment withUrls(@Nullable String localUrl, @Nullable String publicUrl) {
        return new Deployment(
                this.deploymentId,
                this.config,
                this.state,
                localUrl != null ? localUrl : this.localUrl,
                publicUrl != null ? publicUrl : this.publicUrl,
                this.telemetry,
                this.createdAt,
                this.stoppedAt,
                this.errorMessage
        );
    }

    @NonNull
    public Deployment withTelemetry(@NonNull DeploymentTelemetry telemetry) {
        return new Deployment(
                this.deploymentId,
                this.config,
                this.state,
                this.localUrl,
                this.publicUrl,
                telemetry,
                this.createdAt,
                this.stoppedAt,
                this.errorMessage
        );
    }
}
