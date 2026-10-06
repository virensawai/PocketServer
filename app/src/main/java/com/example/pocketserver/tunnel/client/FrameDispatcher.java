package com.example.pocketserver.tunnel.client;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import com.example.pocketserver.tunnel.backpressure.StreamBackpressureController;
import com.example.pocketserver.tunnel.protocol.BinaryFrameEncoder;
import com.example.pocketserver.tunnel.protocol.TunnelControlFrame;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okio.ByteString;

/**
 * Dispatches incoming tunnel requests to the local HTTP request handler, coordinates
 * asynchronous streaming of binary chunks with backpressure, and handles instant stream cancellations.
 */
public class FrameDispatcher {

    private static final String TAG = "FrameDispatcher";

    private final HttpRequestHandler requestHandler;
    private final WebSocketSender webSocketSender;
    private final StreamBackpressureController backpressureController;
    private final ExecutorService streamExecutor;
    private final int bufferSize;

    private final ConcurrentHashMap<String, ActiveStream> activeStreams = new ConcurrentHashMap<>();

    public FrameDispatcher(
            @NonNull HttpRequestHandler requestHandler,
            @NonNull WebSocketSender webSocketSender) {
        this(
                requestHandler,
                webSocketSender,
                new StreamBackpressureController(),
                Executors.newFixedThreadPool(LimitsConfig.MAX_CONCURRENT_REQUESTS),
                LimitsConfig.DEFAULT_BUFFER_CHUNK_SIZE
        );
    }

    public FrameDispatcher(
            @NonNull HttpRequestHandler requestHandler,
            @NonNull WebSocketSender webSocketSender,
            @NonNull StreamBackpressureController backpressureController,
            @NonNull ExecutorService streamExecutor,
            int bufferSize) {
        this.requestHandler = requestHandler;
        this.webSocketSender = webSocketSender;
        this.backpressureController = backpressureController;
        this.streamExecutor = streamExecutor;
        this.bufferSize = bufferSize;
    }

    /**
     * Handles an inbound REQUEST_START frame from the Cloud Gateway.
     */
    public void dispatchRequestStart(@NonNull TunnelControlFrame requestFrame) {
        String requestId = requestFrame.getRequestId();
        if (requestId == null || requestId.isEmpty()) {
            Log.e(TAG, "Rejecting REQUEST_START without requestId");
            return;
        }

        HttpRequest httpRequest = new HttpRequest(
                requestFrame.getMethod() != null ? requestFrame.getMethod() : "GET",
                requestFrame.getPath() != null ? requestFrame.getPath() : "/",
                requestFrame.getHeaders(),
                requestFrame.getQueryString(),
                requestFrame.getClientIp() != null ? requestFrame.getClientIp() : "unknown"
        );

        HttpResponse response = requestHandler.handle(httpRequest);

        // 1. Send RESPONSE_START control frame
        TunnelControlFrame responseStartFrame = TunnelControlFrame.responseStart(
                requestId,
                response.getStatusCode(),
                response.getStatusMessage(),
                response.getHeaders(),
                response.getContentLength()
        );
        webSocketSender.sendText(responseStartFrame.toJson());

        boolean isHead = "HEAD".equalsIgnoreCase(httpRequest.getMethod());

        // 2. Stream Body or complete
        if (response.hasBody() && !isHead && response.getBodyStream() != null) {
            UUID requestUuid = BinaryFrameEncoder.parseOrGenerateUuid(requestId);
            ActiveStream activeStream = new ActiveStream(
                    requestId,
                    requestUuid,
                    response.getBodyStream(),
                    bufferSize
            );

            activeStreams.put(requestId, activeStream);

            streamExecutor.execute(() -> streamResponseBody(activeStream));
        } else {
            // No body: immediately emit STREAM_END
            TunnelControlFrame endFrame = TunnelControlFrame.streamEnd(requestId);
            webSocketSender.sendText(endFrame.toJson());
        }
    }

    private void streamResponseBody(@NonNull ActiveStream activeStream) {
        String requestId = activeStream.getRequestId();
        UUID requestUuid = activeStream.getRequestUuid();

        try (InputStream stream = activeStream.getInputStream()) {
            byte[] buffer = activeStream.getBuffer();

            while (!activeStream.isCancelled()) {
                // Backpressure Check
                if (backpressureController.isCongested(webSocketSender.getQueueSize())) {
                    boolean drained = backpressureController.awaitDrain(
                            webSocketSender,
                            activeStream.getCancellationFlag(),
                            LimitsConfig.MAX_RESPONSE_DURATION_MS
                    );
                    if (!drained || activeStream.isCancelled()) {
                        Log.w(TAG, "Stream " + requestId + " aborted during backpressure wait");
                        break;
                    }
                }

                int bytesRead = stream.read(buffer, 0, buffer.length);
                if (bytesRead == -1) {
                    // EOF reached
                    break;
                }

                if (activeStream.isCancelled()) {
                    break;
                }

                // Encode binary frame (16-byte UUID header + payload chunk)
                ByteString binaryFrame = BinaryFrameEncoder.encodeResponseBody(requestUuid, buffer, 0, bytesRead);
                boolean enqueued = webSocketSender.sendBinary(binaryFrame);
                if (!enqueued) {
                    Log.w(TAG, "Failed to enqueue binary frame for request " + requestId);
                    break;
                }
            }

            // Send STREAM_END if completed without cancellation
            if (!activeStream.isCancelled()) {
                TunnelControlFrame endFrame = TunnelControlFrame.streamEnd(requestId);
                webSocketSender.sendText(endFrame.toJson());
            }

        } catch (Exception e) {
            if (!activeStream.isCancelled()) {
                Log.e(TAG, "Error streaming response for request " + requestId + ": " + e.getMessage());
                TunnelControlFrame errorFrame = TunnelControlFrame.error(requestId, 500, e.getMessage());
                webSocketSender.sendText(errorFrame.toJson());
            }
        } finally {
            activeStream.cancel();
            activeStreams.remove(requestId);
        }
    }

    /**
     * Aborts an active stream immediately upon receiving REQUEST_CANCEL.
     */
    public void cancelRequest(@NonNull String requestId) {
        ActiveStream stream = activeStreams.remove(requestId);
        if (stream != null) {
            stream.cancel();
            Log.d(TAG, "Stream " + requestId + " cancelled and resources recycled");
        }
    }

    /**
     * Aborts all active streams currently in flight.
     */
    public void cancelAll() {
        for (ActiveStream stream : activeStreams.values()) {
            stream.cancel();
        }
        activeStreams.clear();
    }

    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    public void shutdown() {
        cancelAll();
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            streamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
