package com.example.pocketserver.tunnel.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import com.example.pocketserver.tunnel.backpressure.StreamBackpressureController;
import com.example.pocketserver.tunnel.protocol.BinaryFrame;
import com.example.pocketserver.tunnel.protocol.BinaryFrameEncoder;
import com.example.pocketserver.tunnel.protocol.FrameParser;
import com.example.pocketserver.tunnel.protocol.FrameType;
import com.example.pocketserver.tunnel.protocol.TunnelControlFrame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for FrameDispatcher ensuring asynchronous binary streaming,
 * backpressure flow control, and immediate stream cancellation.
 */
public class FrameDispatcherTest {

    private static class MockWebSocketSender implements WebSocketSender {
        final List<String> sentTexts = new CopyOnWriteArrayList<>();
        final List<ByteString> sentBinaries = new CopyOnWriteArrayList<>();
        final AtomicLong queueSize = new AtomicLong(0);

        @Override
        public boolean sendText(@NonNull String text) {
            sentTexts.add(text);
            return true;
        }

        @Override
        public boolean sendBinary(@NonNull ByteString bytes) {
            sentBinaries.add(bytes);
            return true;
        }

        @Override
        public long getQueueSize() {
            return queueSize.get();
        }

        @Override
        public void close(int code, @NonNull String reason) {
        }
    }

    private MockWebSocketSender mockSender;
    private FrameDispatcher dispatcher;

    @Before
    public void setUp() {
        mockSender = new MockWebSocketSender();
    }

    @After
    public void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    public void testDispatchWithStreamingBody() throws Exception {
        byte[] payload = "Hello PocketServer Reverse Multiplexed Streaming Body!".getBytes(StandardCharsets.UTF_8);
        HttpRequestHandler handler = request -> HttpResponse.ok(
                "text/plain",
                new ByteArrayInputStream(payload),
                payload.length,
                "\"etag-abc\"",
                null
        );

        dispatcher = new FrameDispatcher(
                handler,
                mockSender,
                new StreamBackpressureController(),
                Executors.newSingleThreadExecutor(),
                16 // small 16-byte chunk buffer to force multiple chunks
        );

        String requestId = "req_101";
        TunnelControlFrame requestFrame = TunnelControlFrame.requestStart(
                requestId,
                "GET",
                "/hello.txt",
                Collections.emptyMap(),
                null,
                "127.0.0.1"
        );

        dispatcher.dispatchRequestStart(requestFrame);

        // Allow worker thread to stream chunks
        Thread.sleep(100);

        // Verify sent text frames: RESPONSE_START and STREAM_END
        assertEquals(2, mockSender.sentTexts.size());

        TunnelControlFrame respStart = FrameParser.parseTextFrame(mockSender.sentTexts.get(0));
        assertNotNull(respStart);
        assertEquals(FrameType.RESPONSE_START, respStart.getType());
        assertEquals(requestId, respStart.getRequestId());
        assertEquals(200, respStart.getStatusCode());
        assertEquals((long) payload.length, respStart.getContentLength());

        TunnelControlFrame streamEnd = FrameParser.parseTextFrame(mockSender.sentTexts.get(1));
        assertNotNull(streamEnd);
        assertEquals(FrameType.STREAM_END, streamEnd.getType());
        assertEquals(requestId, streamEnd.getRequestId());

        // Verify binary chunks
        assertTrue(mockSender.sentBinaries.size() >= 2);

        ByteArrayOutputStream reconstructed = new ByteArrayOutputStream();
        UUID expectedUuid = BinaryFrameEncoder.parseOrGenerateUuid(requestId);

        for (ByteString chunk : mockSender.sentBinaries) {
            BinaryFrame decoded = BinaryFrameEncoder.decodeResponseBody(chunk);
            assertEquals(expectedUuid, decoded.getRequestId());
            reconstructed.write(decoded.getPayload());
        }

        assertArrayEquals(payload, reconstructed.toByteArray());
        assertEquals(0, dispatcher.getActiveStreamCount());
    }

    @Test
    public void testDispatchWithNoBodyResponse() throws Exception {
        HttpRequestHandler handler = request -> HttpResponse.notModified("\"etag-xyz\"", null);

        dispatcher = new FrameDispatcher(handler, mockSender);

        TunnelControlFrame requestFrame = TunnelControlFrame.requestStart(
                "req_102",
                "GET",
                "/index.html",
                Collections.emptyMap(),
                null,
                "127.0.0.1"
        );

        dispatcher.dispatchRequestStart(requestFrame);

        // Verify text frames: RESPONSE_START followed immediately by STREAM_END
        assertEquals(2, mockSender.sentTexts.size());

        TunnelControlFrame respStart = FrameParser.parseTextFrame(mockSender.sentTexts.get(0));
        assertNotNull(respStart);
        assertEquals(FrameType.RESPONSE_START, respStart.getType());
        assertEquals(304, respStart.getStatusCode());

        TunnelControlFrame streamEnd = FrameParser.parseTextFrame(mockSender.sentTexts.get(1));
        assertNotNull(streamEnd);
        assertEquals(FrameType.STREAM_END, streamEnd.getType());

        // Zero binary frames sent
        assertEquals(0, mockSender.sentBinaries.size());
        assertEquals(0, dispatcher.getActiveStreamCount());
    }

    @Test
    public void testRequestCancelAbortsStreamImmediately() throws Exception {
        // Slow infinite stream
        CountDownLatch firstReadLatch = new CountDownLatch(1);
        AtomicBoolean streamClosed = new AtomicBoolean(false);

        InputStream slowStream = new InputStream() {
            @Override
            public int read() {
                firstReadLatch.countDown();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return -1;
                }
                return 'A';
            }

            @Override
            public void close() {
                streamClosed.set(true);
            }
        };

        HttpRequestHandler handler = request -> HttpResponse.ok(
                "text/plain",
                slowStream,
                100_000L,
                null,
                null
        );

        dispatcher = new FrameDispatcher(handler, mockSender);

        String requestId = "req_cancel_test";
        TunnelControlFrame requestFrame = TunnelControlFrame.requestStart(
                requestId,
                "GET",
                "/infinite.bin",
                Collections.emptyMap(),
                null,
                "127.0.0.1"
        );

        dispatcher.dispatchRequestStart(requestFrame);

        // Wait until streaming has begun
        assertTrue(firstReadLatch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(1, dispatcher.getActiveStreamCount());

        // Abort stream
        dispatcher.cancelRequest(requestId);

        Thread.sleep(150);

        // Active streams cleared and input stream closed
        assertEquals(0, dispatcher.getActiveStreamCount());
        assertTrue(streamClosed.get());

        // STREAM_END should NOT have been sent because it was cancelled
        boolean streamEndSent = false;
        for (String text : mockSender.sentTexts) {
            TunnelControlFrame f = FrameParser.parseTextFrame(text);
            if (f != null && f.getType() == FrameType.STREAM_END) {
                streamEndSent = true;
            }
        }
        assertFalse(streamEndSent);
    }
}
