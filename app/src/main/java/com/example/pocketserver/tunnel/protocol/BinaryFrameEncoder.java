package com.example.pocketserver.tunnel.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import okio.ByteString;

/**
 * High-performance binary frame encoder and decoder.
 * Prefixes binary chunk payloads with a 16-byte UUID header (8 bytes MSB + 8 bytes LSB),
 * eliminating JSON and Base64 encoding overhead on the hot streaming path.
 */
public final class BinaryFrameEncoder {

    public static final int UUID_HEADER_SIZE = 16;

    private BinaryFrameEncoder() {}

    /**
     * Encodes a chunk into a binary WebSocket frame with a 16-byte UUID header.
     *
     * @param requestId UUID identifying the HTTP request stream
     * @param data      source data buffer
     * @param offset    starting offset in buffer
     * @param length    number of payload bytes to encode
     * @return Okio ByteString ready for transmission over WebSocket
     */
    @NonNull
    public static ByteString encodeResponseBody(
            @NonNull UUID requestId,
            @NonNull byte[] data,
            int offset,
            int length) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("Invalid offset/length for payload encoding");
        }

        byte[] frame = new byte[UUID_HEADER_SIZE + length];
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        buffer.putLong(requestId.getMostSignificantBits());
        buffer.putLong(requestId.getLeastSignificantBits());
        buffer.put(data, offset, length);

        return ByteString.of(frame, 0, frame.length);
    }

    /**
     * Encodes the entire payload byte array into a binary frame.
     */
    @NonNull
    public static ByteString encodeResponseBody(@NonNull UUID requestId, @NonNull byte[] data) {
        return encodeResponseBody(requestId, data, 0, data.length);
    }

    /**
     * Decodes a binary WebSocket frame into a {@link BinaryFrame}.
     *
     * @param frameBytes raw frame bytes received over WebSocket
     * @return decoded BinaryFrame containing UUID and raw payload
     * @throws IllegalArgumentException if the frame is shorter than the 16-byte UUID header
     */
    @NonNull
    public static BinaryFrame decodeResponseBody(@NonNull byte[] frameBytes) {
        if (frameBytes.length < UUID_HEADER_SIZE) {
            throw new IllegalArgumentException("Binary frame size (" + frameBytes.length +
                    " bytes) is smaller than required 16-byte UUID header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(frameBytes);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        UUID requestId = new UUID(msb, lsb);

        int payloadLength = frameBytes.length - UUID_HEADER_SIZE;
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new BinaryFrame(requestId, payload);
    }

    /**
     * Decodes a ByteString received from an OkHttp WebSocket.
     */
    @NonNull
    public static BinaryFrame decodeResponseBody(@NonNull ByteString byteString) {
        return decodeResponseBody(byteString.toByteArray());
    }

    /**
     * Converts or deterministically hashes an arbitrary request ID string into a standard UUID.
     * If the string is already a valid UUID string (e.g. "123e4567-e89b-12d3-a456-426614174000"),
     * it is parsed directly; otherwise a type-3 UUID is generated from its UTF-8 bytes.
     */
    @NonNull
    public static UUID parseOrGenerateUuid(@NonNull String requestId) {
        try {
            return UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(requestId.getBytes(StandardCharsets.UTF_8));
        }
    }
}
