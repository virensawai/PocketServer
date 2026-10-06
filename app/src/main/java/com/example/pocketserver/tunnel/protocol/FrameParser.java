package com.example.pocketserver.tunnel.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Parses and validates raw incoming WebSocket text messages into {@link TunnelControlFrame} objects.
 */
public final class FrameParser {

    private FrameParser() {}

    /**
     * Parses a JSON string message into a validated control frame.
     *
     * @param json payload text
     * @return parsed frame or null if JSON is malformed or missing type
     */
    @Nullable
    public static TunnelControlFrame parseTextFrame(@NonNull String json) {
        if (json.trim().isEmpty()) {
            return null;
        }

        TunnelControlFrame frame = TunnelControlFrame.fromJson(json);
        if (frame == null || frame.getType() == null) {
            return null;
        }

        return frame;
    }
}
