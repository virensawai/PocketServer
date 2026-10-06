package com.example.pocketserver.engine.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.core.server.StaticContentService;
import com.example.pocketserver.core.server.adapter.NanoHttpdServerAdapter;
import com.example.pocketserver.core.storage.HybridSafFileResolver;
import com.example.pocketserver.core.util.NetworkUtils;
import com.example.pocketserver.engine.model.Deployment;
import com.example.pocketserver.engine.model.DeploymentConfig;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import com.example.pocketserver.engine.model.HostingMode;
import com.example.pocketserver.engine.model.NetworkPolicy;
import com.example.pocketserver.tunnel.client.TunnelClient;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;

/**
 * Android Foreground Service holding sole ownership of the server lifecycle,
 * local HTTP transport, network monitoring, power locks, and background persistence.
 */
public class ServerForegroundService extends Service {

    private static final String TAG = "ServerForegroundService";

    public static final String ACTION_START = "com.example.pocketserver.action.START";
    public static final String ACTION_STOP = "com.example.pocketserver.action.STOP";
    public static final String ACTION_RESTART = "com.example.pocketserver.action.RESTART";
    public static final String ACTION_UPDATE_POLICY = "com.example.pocketserver.action.UPDATE_POLICY";
    public static final String ACTION_UPDATE_KEEP_AWAKE = "com.example.pocketserver.action.UPDATE_KEEP_AWAKE";

    public static final String EXTRA_CONFIG = "extra_config";
    public static final String EXTRA_DEPLOYMENT_ID = "extra_deployment_id";
    public static final String EXTRA_POLICY = "extra_policy";
    public static final String EXTRA_KEEP_AWAKE = "extra_keep_awake";

    public class LocalBinder extends Binder {
        @NonNull
        public ServerForegroundService getService() {
            return ServerForegroundService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private NotificationHelper notificationHelper;
    private PowerLockManager powerLockManager;
    private NetworkMonitor networkMonitor;
    private DataUsageTracker dataUsageTracker;
    private DeploymentManager deploymentManager;

    private DeploymentConfig activeConfig;
    private DeploymentState currentState = DeploymentState.STOPPED;
    private NanoHttpdServerAdapter serverAdapter;
    private TelemetryTrackingRequestHandler trackingHandler;
    private TunnelClient tunnelClient;

    private String currentLocalUrl;
    private String currentPublicUrl;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper(this);
        powerLockManager = new PowerLockManager(this);
        networkMonitor = new NetworkMonitor(this);
        deploymentManager = DeploymentManager.getInstance(this);

        setupNetworkListener();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Service restarted by OS with START_STICKY but without intent
            if (activeConfig == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_START:
                handleStartAction(intent);
                break;

            case ACTION_STOP:
                handleStopAction();
                break;

            case ACTION_RESTART:
                handleRestartAction(intent);
                break;

            case ACTION_UPDATE_POLICY:
                handleUpdatePolicyAction(intent);
                break;

            case ACTION_UPDATE_KEEP_AWAKE:
                handleUpdateKeepAwakeAction(intent);
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }

        return START_STICKY;
    }

    private void handleStartAction(@NonNull Intent intent) {
        DeploymentConfig config;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            config = intent.getSerializableExtra(EXTRA_CONFIG, DeploymentConfig.class);
        } else {
            config = (DeploymentConfig) intent.getSerializableExtra(EXTRA_CONFIG);
        }
        if (config == null) {
            Log.e(TAG, "Cannot start server: DeploymentConfig missing in intent");
            transitionState(DeploymentState.FAILED, "Deployment configuration missing");
            stopSelf();
            return;
        }

        this.activeConfig = config;

        // 1. Immediately enter foreground state to meet Android SLA
        startForegroundWithNotification();

        // 2. Configure power policy and data guardrails
        powerLockManager.setKeepAwakeEnabled(config.isKeepAwakeWhileLocked());
        networkMonitor.setPolicy(config.getNetworkPolicy());

        dataUsageTracker = new DataUsageTracker(
                config.getDataWarningThresholdBytes(),
                config.getDataLimitThresholdBytes()
        );
        dataUsageTracker.setListener(new DataUsageTracker.DataUsageListener() {
            @Override
            public void onWarningThresholdReached(long bytesTransferred, long warningThreshold) {
                notificationHelper.showDataWarningAlert(bytesTransferred, warningThreshold);
            }

            @Override
            public void onLimitThresholdExceeded(long bytesTransferred, long limitThreshold) {
                notificationHelper.showDataLimitExceededAlert(bytesTransferred, limitThreshold);
                Log.w(TAG, "Data transfer hard cap exceeded! Halting deployment automatically.");
                handleStopAction();
            }
        });

        // 3. Initialize Content Engine
        try {
            Uri rootUri = config.getRootUri();
            if (rootUri == null) {
                throw new IllegalArgumentException("Project root URI is null");
            }

            HybridSafFileResolver fileResolver = new HybridSafFileResolver(this, rootUri);
            StaticContentService staticService = new StaticContentService(fileResolver);
            staticService.setSpaFallbackEnabled(config.isSpaFallbackEnabled());

            trackingHandler = new TelemetryTrackingRequestHandler(
                    staticService,
                    dataUsageTracker,
                    new TelemetryTrackingRequestHandler.TelemetryListener() {
                        @Override
                        public void onTelemetryUpdated(@NonNull DeploymentTelemetry telemetry) {
                            deploymentManager.notifyTelemetryUpdated(telemetry);
                            updateForegroundNotification();
                        }

                        @Override
                        public void onRequestLogged(@NonNull com.example.pocketserver.engine.model.RequestLogItem logItem) {
                            deploymentManager.notifyRequestLogged(logItem);
                        }
                    }
            );

            // 4. Start Local NanoHTTPD Adapter
            int port = config.getLocalPort();
            serverAdapter = new NanoHttpdServerAdapter("0.0.0.0", port, trackingHandler);
            serverAdapter.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            String localIp = NetworkUtils.getLocalIpAddress();
            currentLocalUrl = "http://" + localIp + ":" + port;

            if (config.getHostingMode() == HostingMode.PUBLIC) {
                transitionState(DeploymentState.CONNECTING, null);
                String relayUrl = LimitsConfig.DEFAULT_RELAY_URL;
                String deploymentId = intent.getStringExtra(EXTRA_DEPLOYMENT_ID);
                if (deploymentId == null || deploymentId.isEmpty()) {
                    deploymentId = "dep_" + Long.toHexString(System.currentTimeMillis());
                }

                tunnelClient = new TunnelClient(
                        relayUrl,
                        deploymentId,
                        config.getProjectId(),
                        null,
                        config.getPublicHostname(),
                        trackingHandler,
                        new TunnelClient.TunnelStateListener() {
                            @Override
                            public void onConnecting() {
                                transitionState(DeploymentState.CONNECTING, null);
                                updateForegroundNotification();
                            }

                            @Override
                            public void onRegistered(@NonNull String publicUrl, @NonNull String assignedHostname) {
                                currentPublicUrl = publicUrl;
                                deploymentManager.notifyUrlsAssigned(currentLocalUrl, currentPublicUrl);
                                transitionState(DeploymentState.LIVE, null);
                                updateForegroundNotification();
                            }

                            @Override
                            public void onDisconnected(int code, @NonNull String reason) {
                                if (currentState == DeploymentState.LIVE) {
                                    transitionState(DeploymentState.NETWORK_LOST, "Tunnel disconnected: " + reason);
                                    updateForegroundNotification();
                                }
                            }

                            @Override
                            public void onReconnecting(int attempt, long delayMs) {
                                transitionState(DeploymentState.RECONNECTING, null);
                                updateForegroundNotification();
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                Log.e(TAG, "Tunnel error: " + error.getMessage(), error);
                                if (currentState != DeploymentState.LIVE) {
                                    transitionState(DeploymentState.FAILED, "Tunnel failed: " + error.getMessage());
                                }
                            }
                        }
                );
                tunnelClient.connect();
            } else {
                // Local Mode: directly transition to LIVE
                currentPublicUrl = null;
                deploymentManager.notifyUrlsAssigned(currentLocalUrl, null);
                transitionState(DeploymentState.LIVE, null);
            }

            // 5. Start Network Monitoring
            networkMonitor.startMonitoring();

            updateForegroundNotification();

        } catch (IOException e) {
            Log.e(TAG, "Failed to bind local server port: " + e.getMessage(), e);
            transitionState(DeploymentState.FAILED, "Port " + config.getLocalPort() + " error: " + e.getMessage());
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Server initialization failed: " + e.getMessage(), e);
            transitionState(DeploymentState.FAILED, e.getMessage());
            stopSelf();
        }
    }

    private void handleStopAction() {
        stopServerInternal();
        transitionState(DeploymentState.STOPPED, null);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void handleRestartAction(@NonNull Intent intent) {
        stopServerInternal();
        handleStartAction(intent);
    }

    private void handleUpdatePolicyAction(@NonNull Intent intent) {
        NetworkPolicy policy;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            policy = intent.getSerializableExtra(EXTRA_POLICY, NetworkPolicy.class);
        } else {
            policy = (NetworkPolicy) intent.getSerializableExtra(EXTRA_POLICY);
        }
        if (policy != null) {
            networkMonitor.setPolicy(policy);
        }
    }

    private void handleUpdateKeepAwakeAction(@NonNull Intent intent) {
        boolean keepAwake = intent.getBooleanExtra(EXTRA_KEEP_AWAKE, false);
        powerLockManager.setKeepAwakeEnabled(keepAwake);
    }

    private void stopServerInternal() {
        if (serverAdapter != null) {
            try {
                serverAdapter.stop();
            } catch (Exception ignored) {
            }
            serverAdapter = null;
        }

        if (tunnelClient != null) {
            try {
                tunnelClient.disconnect();
            } catch (Exception ignored) {
            }
            tunnelClient = null;
        }

        networkMonitor.stopMonitoring();
        powerLockManager.releaseAll();

        currentLocalUrl = null;
        currentPublicUrl = null;
        activeConfig = null;
    }

    private void setupNetworkListener() {
        networkMonitor.setListener(new NetworkMonitor.NetworkStateListener() {
            @Override
            public void onNetworkAvailable(boolean isWifi, boolean isCellular, boolean isMetered) {
                if (currentState == DeploymentState.NETWORK_LOST || currentState == DeploymentState.RECONNECTING) {
                    transitionState(DeploymentState.RECONNECTING, null);
                    // Re-resolve local IP on network return
                    if (serverAdapter != null && activeConfig != null) {
                        String localIp = NetworkUtils.getLocalIpAddress();
                        currentLocalUrl = "http://" + localIp + ":" + activeConfig.getLocalPort();
                        deploymentManager.notifyUrlsAssigned(currentLocalUrl, currentPublicUrl);
                    }
                    if (activeConfig != null && activeConfig.getHostingMode() == HostingMode.PUBLIC) {
                        if (tunnelClient != null && !tunnelClient.isConnected()) {
                            tunnelClient.connect();
                        }
                    } else {
                        transitionState(DeploymentState.LIVE, null);
                    }
                    updateForegroundNotification();
                }
            }

            @Override
            public void onNetworkLost() {
                if (currentState == DeploymentState.LIVE) {
                    transitionState(DeploymentState.NETWORK_LOST, "Network connection lost");
                    updateForegroundNotification();
                }
            }

            @Override
            public void onPolicyViolation(@NonNull NetworkPolicy policy) {
                if (currentState == DeploymentState.LIVE) {
                    transitionState(DeploymentState.NETWORK_LOST, "Mobile network detected: Wi-Fi Only policy active");
                    updateForegroundNotification();
                }
            }
        });
    }

    private void transitionState(@NonNull DeploymentState newState, @Nullable String errorMessage) {
        if (currentState.canTransitionTo(newState)) {
            this.currentState = newState;
            powerLockManager.onDeploymentStateChanged(newState);
            deploymentManager.notifyStateChanged(newState, errorMessage);
        } else {
            Log.w(TAG, "Illegal state transition attempted: " + currentState + " -> " + newState);
        }
    }

    private void startForegroundWithNotification() {
        Notification notification = notificationHelper.buildServiceNotification(
                deploymentManager.getActiveDeployment().getValue(),
                trackingHandler != null ? trackingHandler.getSnapshot() : new DeploymentTelemetry()
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            }
            ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification, serviceType);
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification);
        }
    }

    private void updateForegroundNotification() {
        if (currentState.isActive()) {
            Notification notification = notificationHelper.buildServiceNotification(
                    deploymentManager.getActiveDeployment().getValue(),
                    trackingHandler != null ? trackingHandler.getSnapshot() : null
            );
            notificationHelper.createNotificationChannels();
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopServerInternal();
        super.onDestroy();
    }
}
