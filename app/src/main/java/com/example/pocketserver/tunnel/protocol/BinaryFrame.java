package com.example.pocketserver.tunnel.protocol;

import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * Model representing a decoded binary response chunk frame.
 */
public class BinaryFrame {

    private final UUID requestId;
    private final byte[] payload;

    public BinaryFrame(@NonNull UUID requestId, @NonNull byte[] payload) {
        this.requestId = requestId;
        this.payload = payload;
    }

    @NonNull
    public UUID getRequestId() {
        return requestId;
    }

    @NonNull
    public byte[] getPayload() {
        return payload;
    }

    public int getPayloadLength() {
        return payload.length;
    }
}
