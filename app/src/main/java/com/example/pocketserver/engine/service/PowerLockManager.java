package com.example.pocketserver.engine.service;

import android.content.Context;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.engine.model.DeploymentState;

/**
 * Manages device CPU wake locks strictly adhering to the smart power policy.
 * Wake locks are NEVER held by default. They are only acquired when the user
 * has explicitly enabled "Keep Server Awake While Locked" AND the deployment
 * is in the LIVE state. Locks are immediately released upon STOPPED, FAILED, or NETWORK_LOST.
 */
public class PowerLockManager {

    private static final String WAKE_LOCK_TAG = "PocketServer:ServerWakeLock";

    private final Context context;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean keepAwakeEnabled = false;
    private volatile DeploymentState currentState = DeploymentState.STOPPED;

    public PowerLockManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void setKeepAwakeEnabled(boolean enabled) {
        this.keepAwakeEnabled = enabled;
        evaluateWakeLock();
    }

    public boolean isKeepAwakeEnabled() {
        return keepAwakeEnabled;
    }

    public synchronized void onDeploymentStateChanged(@NonNull DeploymentState newState) {
        this.currentState = newState;
        evaluateWakeLock();
    }

    private synchronized void evaluateWakeLock() {
        if (keepAwakeEnabled && currentState == DeploymentState.LIVE) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    private synchronized void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            try {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            } catch (Exception ignored) {
                // Ignore security or power exceptions gracefully
            }
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
        wakeLock = null;
    }

    public synchronized void releaseAll() {
        releaseWakeLock();
        currentState = DeploymentState.STOPPED;
    }

    public synchronized boolean isHoldingWakeLock() {
        return wakeLock != null && wakeLock.isHeld();
    }
}
