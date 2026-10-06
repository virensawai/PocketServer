package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;

/**
 * Guardrail policy defining which network interfaces may be utilized for server hosting.
 */
public enum NetworkPolicy {
    /**
     * Only allow hosting and tunnel relay over unmetered Wi-Fi connections.
     */
    WIFI_ONLY("Wi-Fi Only"),

    /**
     * Allow hosting over both Wi-Fi and cellular mobile data.
     */
    WIFI_AND_MOBILE("Wi-Fi + Mobile Data");

    private final String displayName;

    NetworkPolicy(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
