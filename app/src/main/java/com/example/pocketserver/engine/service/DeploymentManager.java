package com.example.pocketserver.engine.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.pocketserver.engine.model.Deployment;
import com.example.pocketserver.engine.model.DeploymentConfig;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import com.example.pocketserver.engine.model.NetworkPolicy;
import java.util.UUID;

/**
 * Central singleton orchestrating deployment lifecycles, configuration, and telemetry.
 * Mediates between UI ViewModels and the background {@link ServerForegroundService}.
 */
public class DeploymentManager {

    private static volatile DeploymentManager instance;

    public static DeploymentManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DeploymentManager.class) {
                if (instance == null) {
                    instance = new DeploymentManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context appContext;

    private final MutableLiveData<DeploymentState> deploymentState = new MutableLiveData<>(DeploymentState.STOPPED);
    private final MutableLiveData<Deployment> activeDeployment = new MutableLiveData<>(null);
    private final MutableLiveData<DeploymentTelemetry> telemetry = new MutableLiveData<>(new DeploymentTelemetry());
    private final MutableLiveData<NetworkPolicy> networkPolicy = new MutableLiveData<>(NetworkPolicy.WIFI_AND_MOBILE);
    private final MutableLiveData<java.util.List<com.example.pocketserver.engine.model.RequestLogItem>> recentRequests =
            new MutableLiveData<>(new java.util.ArrayList<>());
    private final MutableLiveData<Boolean> keepAwakeEnabled = new MutableLiveData<>(false);

    private DeploymentConfig currentConfig;

    private DeploymentManager(@NonNull Context appContext) {
        this.appContext = appContext;
    }

    // --- Observable LiveData streams for UI layer ---

    @NonNull
    public LiveData<DeploymentState> getDeploymentState() {
        return deploymentState;
    }

    @NonNull
    public LiveData<Deployment> getActiveDeployment() {
        return activeDeployment;
    }

    @NonNull
    public LiveData<DeploymentTelemetry> getTelemetry() {
        return telemetry;
    }

    @NonNull
    public LiveData<java.util.List<com.example.pocketserver.engine.model.RequestLogItem>> getRecentRequests() {
        return recentRequests;
    }

    @NonNull
    public LiveData<NetworkPolicy> getNetworkPolicy() {
        return networkPolicy;
    }

    @NonNull
    public LiveData<Boolean> getKeepAwakeEnabled() {
        return keepAwakeEnabled;
    }

    // --- Control Operations ---

    /**
     * Initiates deployment execution by dispatching start intent to {@link ServerForegroundService}.
     */
    public synchronized void startDeployment(@NonNull DeploymentConfig config) {
        this.currentConfig = config;
        this.networkPolicy.postValue(config.getNetworkPolicy());
        this.keepAwakeEnabled.postValue(config.isKeepAwakeWhileLocked());
        this.recentRequests.postValue(new java.util.ArrayList<>());

        String deploymentId = "dep_" + UUID.randomUUID().toString().substring(0, 8);
        DeploymentTelemetry initialTelemetry = new DeploymentTelemetry();
        this.telemetry.postValue(initialTelemetry);

        Deployment newDeployment = new Deployment(
                deploymentId,
                config,
                DeploymentState.STARTING,
                null,
                null,
                initialTelemetry,
                System.currentTimeMillis(),
                0,
                null
        );
        this.activeDeployment.postValue(newDeployment);
        this.deploymentState.postValue(DeploymentState.STARTING);

        try {
            Intent intent = new Intent(appContext, ServerForegroundService.class);
            intent.setAction(ServerForegroundService.ACTION_START);
            intent.putExtra(ServerForegroundService.EXTRA_CONFIG, config);
            intent.putExtra(ServerForegroundService.EXTRA_DEPLOYMENT_ID, deploymentId);
            ContextCompat.startForegroundService(appContext, intent);
        } catch (Exception ignored) {
            // Fallback in unit test environments
        }
    }

    /**
     * Stops the active deployment and halts the background service.
     */
    public synchronized void stopDeployment() {
        try {
            Intent intent = new Intent(appContext, ServerForegroundService.class);
            intent.setAction(ServerForegroundService.ACTION_STOP);
            appContext.startService(intent);
        } catch (Exception ignored) {
            // Fallback in unit test environments
        }

        // Optimistically update if service was already dead
        Deployment current = activeDeployment.getValue();
        if (current != null && current.getState() != DeploymentState.STOPPED) {
            this.activeDeployment.postValue(current.withState(DeploymentState.STOPPED, null));
            this.deploymentState.postValue(DeploymentState.STOPPED);
        }
    }

    /**
     * Restarts the current deployment with existing configuration.
     */
    public synchronized void restartDeployment() {
        if (currentConfig != null) {
            stopDeployment();
            startDeployment(currentConfig);
        }
    }

    public synchronized void setNetworkPolicy(@NonNull NetworkPolicy policy) {
        this.networkPolicy.postValue(policy);
        try {
            Intent intent = new Intent(appContext, ServerForegroundService.class);
            intent.setAction(ServerForegroundService.ACTION_UPDATE_POLICY);
            intent.putExtra(ServerForegroundService.EXTRA_POLICY, policy);
            appContext.startService(intent);
        } catch (Exception ignored) {}
    }

    public synchronized void setKeepAwakeEnabled(boolean enabled) {
        this.keepAwakeEnabled.postValue(enabled);
        try {
            Intent intent = new Intent(appContext, ServerForegroundService.class);
            intent.setAction(ServerForegroundService.ACTION_UPDATE_KEEP_AWAKE);
            intent.putExtra(ServerForegroundService.EXTRA_KEEP_AWAKE, enabled);
            appContext.startService(intent);
        } catch (Exception ignored) {}
    }

    // --- Service Bridge Callbacks ---

    public synchronized void notifyStateChanged(@NonNull DeploymentState newState, @Nullable String errorMessage) {
        Deployment current = activeDeployment.getValue();
        if (current != null) {
            Deployment updated = current.withState(newState, errorMessage);
            this.activeDeployment.postValue(updated);
        }
        this.deploymentState.postValue(newState);
    }

    public synchronized void notifyUrlsAssigned(@Nullable String localUrl, @Nullable String publicUrl) {
        Deployment current = activeDeployment.getValue();
        if (current != null) {
            Deployment updated = current.withUrls(localUrl, publicUrl);
            this.activeDeployment.postValue(updated);
        }
    }

    public synchronized void notifyTelemetryUpdated(@NonNull DeploymentTelemetry newTelemetry) {
        this.telemetry.postValue(newTelemetry);
        Deployment current = activeDeployment.getValue();
        if (current != null) {
            this.activeDeployment.postValue(current.withTelemetry(newTelemetry));
        }
    }

    public synchronized void notifyRequestLogged(@NonNull com.example.pocketserver.engine.model.RequestLogItem logItem) {
        java.util.List<com.example.pocketserver.engine.model.RequestLogItem> current = recentRequests.getValue();
        java.util.List<com.example.pocketserver.engine.model.RequestLogItem> updated =
                current != null ? new java.util.ArrayList<>(current) : new java.util.ArrayList<>();
        if (updated.size() >= 50) {
            updated.remove(updated.size() - 1);
        }
        updated.add(0, logItem);
        recentRequests.postValue(updated);
    }

    public synchronized void notifyServiceStopped() {
        notifyStateChanged(DeploymentState.STOPPED, null);
    }
}
