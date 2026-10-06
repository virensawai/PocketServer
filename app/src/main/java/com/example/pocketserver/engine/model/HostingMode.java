package com.example.pocketserver.engine.model;

import androidx.annotation.NonNull;

/**
 * Hosting operating modes supported by PocketServer.
 */
public enum HostingMode {
    /**
     * Local Area Network mode (e.g. http://192.168.1.15:8080).
     * Binds local server directly to device Wi-Fi/Ethernet interface without cloud tunnel.
     */
    LOCAL("Local Network"),

    /**
     * Public Internet mode (e.g. https://xyz.pocketserver.dev).
     * Establishes outbound multiplexed WebSocket tunnel to the Cloud Gateway.
     */
    PUBLIC("Public Internet");

    private final String displayName;

    HostingMode(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
