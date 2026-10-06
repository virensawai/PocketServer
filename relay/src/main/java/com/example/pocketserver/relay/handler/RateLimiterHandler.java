package com.example.pocketserver.relay.handler;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty inbound handler enforcing Multi-Tier Rate Limiting:
 * Tier 1 (Visitor IP): Maximum requests per second (token-bucket) and max concurrent connections.
 */
@ChannelHandler.Sharable
public class RateLimiterHandler extends ChannelInboundHandlerAdapter {

    private final GatewayConfig config;
    private final GatewayMetrics metrics;

    private final ConcurrentHashMap<String, TokenBucket> ipTokenBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> ipConcurrentConnections = new ConcurrentHashMap<>();

    public RateLimiterHandler(GatewayConfig config, GatewayMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String clientIp = extractClientIp(ctx, null);
        ipConcurrentConnections.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientIp = extractClientIp(ctx, null);
        AtomicInteger count = ipConcurrentConnections.get(clientIp);
        if (count != null) {
            if (count.decrementAndGet() <= 0) {
                ipConcurrentConnections.remove(clientIp);
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        HttpRequest request = (HttpRequest) msg;

        // Bypass rate limiting for tunnel WebSocket handshake endpoint
        if (request.uri().startsWith(config.getTunnelPath())) {
            ctx.fireChannelRead(msg);
            return;
        }

        String clientIp = extractClientIp(ctx, request);

        // 1. Check concurrent connection limit per IP
        AtomicInteger activeConnections = ipConcurrentConnections.get(clientIp);
        if (activeConnections != null && activeConnections.get() > config.getMaxConcurrentConnectionsPerIp()) {
            metrics.incrementRateLimitedRequests();
            sendRateLimitError(ctx, request, "Concurrent connection limit exceeded for client IP");
            return;
        }

        // 2. Check token bucket rate limit per IP
        TokenBucket bucket = ipTokenBuckets.computeIfAbsent(
                clientIp,
                k -> new TokenBucket(config.getMaxVisitorRequestsPerSecondPerIp(), config.getMaxVisitorRequestsPerSecondPerIp())
        );

        if (!bucket.tryAcquire()) {
            metrics.incrementRateLimitedRequests();
            sendRateLimitError(ctx, request, "Request rate limit exceeded. Please slow down.");
            return;
        }

        ctx.fireChannelRead(msg);
    }

    private void sendRateLimitError(ChannelHandlerContext ctx, HttpRequest request, String detail) {
        String json = "{\"error\":\"Too Many Requests\",\"status\":429,\"message\":\"" + detail + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.TOO_MANY_REQUESTS,
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.RETRY_AFTER, "1");

        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (!keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        }
    }

    public static String extractClientIp(ChannelHandlerContext ctx, HttpRequest request) {
        if (request != null) {
            String forwardedFor = request.headers().get("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                int commaIdx = forwardedFor.indexOf(',');
                return (commaIdx > 0 ? forwardedFor.substring(0, commaIdx) : forwardedFor).trim();
            }
            String realIp = request.headers().get("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.trim();
            }
        }

        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    /**
     * Non-blocking token bucket algorithm for per-IP rate limiting.
     */
    public static class TokenBucket {
        private final long capacity;
        private final double refillRatePerMs;
        private double availableTokens;
        private long lastRefillTimeMs;

        public TokenBucket(long capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillRatePerMs = refillPerSecond / 1000.0;
            this.availableTokens = capacity;
            this.lastRefillTimeMs = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimeMs;
            if (elapsed > 0) {
                availableTokens = Math.min(capacity, availableTokens + (elapsed * refillRatePerMs));
                lastRefillTimeMs = now;
            }
        }
    }
}
