package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;

/**
 * Formal deployment lifecycle states defined by the PocketServer state machine.
 *
 * <pre>
 *   STOPPED -> STARTING -> CONNECTING -> LIVE
 *                 |            |            |
 *                 v            v            v
 *               FAILED       FAILED    NETWORK_LOST
 *                                           |
 *                                           v
 *                                      RECONNECTING -> LIVE / FAILED
 * </pre>
 */
public enum DeploymentState {
    STOPPED("Stopped"),
    STARTING("Starting"),
    CONNECTING("Connecting"),
    LIVE("Live"),
    NETWORK_LOST("Network Lost"),
    RECONNECTING("Reconnecting"),
    FAILED("Failed");

    private final String displayName;

    DeploymentState(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Verifies whether a transition from this state to the target state is valid.
     *
     * @param target the proposed subsequent state
     * @return true if the transition obeys the state machine rules
     */
    public boolean canTransitionTo(@NonNull DeploymentState target) {
        if (this == target) {
            return true;
        }

        switch (this) {
            case STOPPED:
                return target == STARTING;

            case STARTING:
                // Can move to CONNECTING (Public/Tunnel), LIVE (Local mode directly), FAILED, or STOPPED (user cancel)
                return target == CONNECTING || target == LIVE || target == FAILED || target == STOPPED;

            case CONNECTING:
                // Handshake success -> LIVE, rejection -> FAILED, user abort -> STOPPED, network lost -> NETWORK_LOST
                return target == LIVE || target == FAILED || target == STOPPED || target == NETWORK_LOST;

            case LIVE:
                // Normal stop, network drop, or server runtime failure
                return target == STOPPED || target == NETWORK_LOST || target == FAILED;

            case NETWORK_LOST:
                // Network restored -> RECONNECTING, user cancels -> STOPPED, unrecoverable timeout -> FAILED
                return target == RECONNECTING || target == STOPPED || target == FAILED;

            case RECONNECTING:
                // Handshake OK -> LIVE, network lost again -> NETWORK_LOST, max backoff exceeded -> FAILED, user abort -> STOPPED
                return target == LIVE || target == NETWORK_LOST || target == FAILED || target == STOPPED;

            case FAILED:
                // Reset/dismiss -> STOPPED, or user taps Retry -> STARTING
                return target == STOPPED || target == STARTING;

            default:
                return false;
        }
    }

    /**
     * Indicates whether the deployment is actively allocated and running (or attempting to run).
     */
    public boolean isActive() {
        return this != STOPPED && this != FAILED;
    }

    /**
     * Indicates whether the deployment is currently operational and serving traffic.
     */
    public boolean isRunning() {
        return this == LIVE;
    }
}
