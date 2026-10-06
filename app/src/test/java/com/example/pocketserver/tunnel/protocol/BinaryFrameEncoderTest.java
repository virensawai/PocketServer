package com.example.pocketserver.tunnel.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import okio.ByteString;
import org.junit.Test;

/**
 * Unit tests for BinaryFrameEncoder verifying 16-byte UUID header construction,
 * zero-copy payload packing, and decoding.
 */
public class BinaryFrameEncoderTest {

    @Test
    public void testEncodeAndDecodeBinaryFrame() {
        UUID expectedUuid = UUID.randomUUID();
        byte[] payload = "PocketServer Binary Chunk Payload 12345".getBytes(StandardCharsets.UTF_8);

        ByteString encoded = BinaryFrameEncoder.encodeResponseBody(expectedUuid, payload);
        assertNotNull(encoded);
        assertEquals(BinaryFrameEncoder.UUID_HEADER_SIZE + payload.length, encoded.size());

        BinaryFrame decoded = BinaryFrameEncoder.decodeResponseBody(encoded);
        assertNotNull(decoded);
        assertEquals(expectedUuid, decoded.getRequestId());
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    public void testEncodeWithOffsetAndLength() {
        UUID expectedUuid = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        byte[] fullBuffer = new byte[100];
        byte[] chunk = "SliceChunk".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(chunk, 0, fullBuffer, 10, chunk.length);

        ByteString encoded = BinaryFrameEncoder.encodeResponseBody(expectedUuid, fullBuffer, 10, chunk.length);
        assertEquals(BinaryFrameEncoder.UUID_HEADER_SIZE + chunk.length, encoded.size());

        BinaryFrame decoded = BinaryFrameEncoder.decodeResponseBody(encoded.toByteArray());
        assertEquals(expectedUuid, decoded.getRequestId());
        assertArrayEquals(chunk, decoded.getPayload());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeFailsWhenFrameSmallerThanHeader() {
        byte[] truncated = new byte[15]; // less than 16 bytes
        BinaryFrameEncoder.decodeResponseBody(truncated);
    }

    @Test
    public void testParseOrGenerateUuid() {
        // Valid UUID string
        String validUuidStr = "550e8400-e29b-41d4-a716-446655440000";
        UUID parsed = BinaryFrameEncoder.parseOrGenerateUuid(validUuidStr);
        assertEquals(UUID.fromString(validUuidStr), parsed);

        // Non-UUID request ID (e.g. req_101)
        UUID generated1 = BinaryFrameEncoder.parseOrGenerateUuid("req_101");
        UUID generated2 = BinaryFrameEncoder.parseOrGenerateUuid("req_101");
        assertNotNull(generated1);
        assertEquals(generated1, generated2); // Deterministic
    }
}
