package com.example.pocketserver.relay.handler;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import com.example.pocketserver.relay.model.ActiveTunnel;
import com.example.pocketserver.relay.model.PendingVisitorRequest;
import com.example.pocketserver.relay.protocol.GatewayControlFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.ScheduledFuture;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates high-throughput HTTP chunked streaming between external visitors and Android reverse tunnels.
 * Supports zero-allocation binary forwarding, backpressure, timeout management, and client disconnect cancellation.
 */
public class HttpChunkedStreamer {

    private final GatewayConfig config;
    private final GatewayMetrics metrics;

    private final ConcurrentHashMap<UUID, PendingVisitorRequest> pendingRequests = new ConcurrentHashMap<>();

    public HttpChunkedStreamer(GatewayConfig config, GatewayMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Bridges an inbound visitor HTTP request to an active Android tunnel.
     */
    public void handleVisitorRequest(ChannelHandlerContext visitorCtx, FullHttpRequest request, ActiveTunnel tunnel) {
        // Enforce deployment-tier stream concurrency limit
        if (tunnel.getActiveStreamCount() >= config.getMaxConcurrentStreamsPerDeployment()) {
            metrics.incrementRateLimitedRequests();
            sendErrorResponse(visitorCtx, request, HttpResponseStatus.TOO_MANY_REQUESTS,
                    "Deployment stream limit reached (max " + config.getMaxConcurrentStreamsPerDeployment() + " concurrent streams)");
            return;
        }

        UUID requestId = UUID.randomUUID();
        tunnel.incrementActiveStreamCount();
        metrics.incrementTotalRequests();
        metrics.incrementActiveRequests();

        boolean keepAlive = HttpUtil.isKeepAlive(request);
        PendingVisitorRequest pending = new PendingVisitorRequest(requestId, visitorCtx, tunnel, keepAlive);

        // 1. Setup Request Timeout Handler
        ScheduledFuture<?> timeoutFuture = visitorCtx.executor().schedule(() -> {
            if (pending.markCompleted()) {
                pendingRequests.remove(requestId);
                tunnel.decrementActiveStreamCount();
                metrics.decrementActiveRequests();
                metrics.incrementTimeoutRequests();

                // Notify Android to abort SAF reading and release memory
                tunnel.sendControlFrame(GatewayControlFrame.requestCancel(requestId.toString()).toJson());

                if (visitorCtx.channel().isActive()) {
                    if (!pending.areHeadersSent()) {
                        sendErrorResponse(visitorCtx, request, HttpResponseStatus.GATEWAY_TIMEOUT,
                                "Device response timed out after " + config.getRequestTimeoutMs() + "ms");
                    } else {
                        visitorCtx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                                .addListener(ChannelFutureListener.CLOSE);
                    }
                }
            }
        }, config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

        pending.setTimeoutFuture(timeoutFuture);

        // 2. Setup Visitor Channel Close Listener (instant cancel on tab close / network drop)
        visitorCtx.channel().closeFuture().addListener(future -> {
            if (pending.markCompleted()) {
                pendingRequests.remove(requestId);
                tunnel.decrementActiveStreamCount();
                metrics.decrementActiveRequests();
                metrics.incrementCancelledRequests();

                // Prompt Android to abort streaming immediately
                tunnel.sendControlFrame(GatewayControlFrame.requestCancel(requestId.toString()).toJson());
            }
        });

        pendingRequests.put(requestId, pending);

        // 3. Serialize request and dispatch REQUEST_START frame to Android
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers()) {
            headers.put(entry.getKey(), entry.getValue());
        }

        String clientIp = RateLimiterHandler.extractClientIp(visitorCtx, request);
        String uri = request.uri();
        String path = uri;
        String queryString = null;
        int queryIdx = uri.indexOf('?');
        if (queryIdx >= 0) {
            path = uri.substring(0, queryIdx);
            queryString = uri.substring(queryIdx + 1);
        }

        GatewayControlFrame requestStart = GatewayControlFrame.requestStart(
                requestId.toString(),
                request.method().name(),
                path,
                headers,
                queryString,
                clientIp
        );

        tunnel.sendControlFrame(requestStart.toJson());
    }

    /**
     * Handles RESPONSE_START control frame from Android and writes HTTP headers to visitor.
     */
    public void handleResponseStart(GatewayControlFrame frame) {
        if (frame.getRequestId() == null) {
            return;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(frame.getRequestId());
        } catch (IllegalArgumentException e) {
            return;
        }

        PendingVisitorRequest pending = pendingRequests.get(requestId);
        if (pending == null || pending.isCompleted()) {
            return;
        }

        ChannelHandlerContext vCtx = pending.getVisitorCtx();
        if (!vCtx.channel().isActive()) {
            return;
        }

        if (pending.markHeadersSent()) {
            HttpResponseStatus status = HttpResponseStatus.valueOf(frame.getStatusCode());
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

            for (Map.Entry<String, String> header : frame.getHeaders().entrySet()) {
                response.headers().set(header.getKey(), header.getValue());
            }

            long contentLength = frame.getContentLength();
            if (contentLength >= 0) {
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
            } else {
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }

            if (pending.isKeepAlive()) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }

            vCtx.writeAndFlush(response);
        }
    }

    /**
     * Handles binary chunk from Android. Extracts 16-byte UUID header and forwards payload to visitor with zero copy.
     */
    public void handleResponseBody(ByteBuf content) {
        if (content.readableBytes() < 16) {
            return;
        }

        long msb = content.readLong();
        long lsb = content.readLong();
        UUID requestId = new UUID(msb, lsb);

        PendingVisitorRequest pending = pendingRequests.get(requestId);
        if (pending == null || pending.isCompleted()) {
            return;
        }

        ChannelHandlerContext vCtx = pending.getVisitorCtx();
        if (!vCtx.channel().isActive()) {
            return;
        }

        int payloadLength = content.readableBytes();
        if (payloadLength > 0) {
            ByteBuf payload = content.retain();
            pending.getTunnel().addBytesStreamed(payloadLength);
            metrics.recordBytesTransferred(payloadLength);

            vCtx.writeAndFlush(new DefaultHttpContent(payload));
        }
    }

    /**
     * Handles STREAM_END control frame from Android, writing the final HTTP chunk to visitor.
     */
    public void handleStreamEnd(String requestIdStr) {
        if (requestIdStr == null) {
            return;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(requestIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        PendingVisitorRequest pending = pendingRequests.remove(requestId);
        if (pending != null && pending.markCompleted()) {
            pending.getTunnel().decrementActiveStreamCount();
            pending.getTunnel().incrementRequestsServed();
            metrics.decrementActiveRequests();

            ChannelHandlerContext vCtx = pending.getVisitorCtx();
            if (vCtx.channel().isActive()) {
                ChannelFuture future = vCtx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!pending.isKeepAlive()) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
    }

    /**
     * Cancels all pending requests associated with a disconnected tunnel.
     */
    public void cancelAllForTunnel(ActiveTunnel tunnel) {
        for (Map.Entry<UUID, PendingVisitorRequest> entry : pendingRequests.entrySet()) {
            PendingVisitorRequest pending = entry.getValue();
            if (pending.getTunnel() == tunnel) {
                if (pending.markCompleted()) {
                    pendingRequests.remove(entry.getKey());
                    tunnel.decrementActiveStreamCount();
                    metrics.decrementActiveRequests();

                    ChannelHandlerContext vCtx = pending.getVisitorCtx();
                    if (vCtx.channel().isActive()) {
                        if (!pending.areHeadersSent()) {
                            sendSimpleError(vCtx, HttpResponseStatus.BAD_GATEWAY, "Device tunnel disconnected");
                        } else {
                            vCtx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                }
            }
        }
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String msg) {
        String json = "{\"error\":\"" + status.reasonPhrase() + "\",\"status\":" + status.code() + ",\"message\":\"" + msg + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (!keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        }
    }

    private void sendSimpleError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        String json = "{\"error\":\"" + status.reasonPhrase() + "\",\"message\":\"" + msg + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
}
