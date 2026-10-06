package com.example.pocketserver.relay.router;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.handler.HttpChunkedStreamer;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import com.example.pocketserver.relay.model.ActiveTunnel;
import com.example.pocketserver.relay.registry.TunnelRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Netty inbound handler inspecting the Host header to route visitor traffic
 * to the registered Android reverse tunnel, or forwarding /tunnel requests to the WebSocket handler.
 */
@ChannelHandler.Sharable
public class SubdomainRouter extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final GatewayConfig config;
    private final TunnelRegistry tunnelRegistry;
    private final HttpChunkedStreamer chunkedStreamer;
    private final GatewayMetrics metrics;

    public SubdomainRouter(
            GatewayConfig config,
            TunnelRegistry tunnelRegistry,
            HttpChunkedStreamer chunkedStreamer,
            GatewayMetrics metrics) {
        this.config = config;
        this.tunnelRegistry = tunnelRegistry;
        this.chunkedStreamer = chunkedStreamer;
        this.metrics = metrics;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        // 1. Forward /tunnel WebSocket handshakes to downstream WebSocket handler
        if (uri.startsWith(config.getTunnelPath())) {
            ctx.fireChannelRead(request.retain());
            return;
        }

        // 2. Extract host from Host header, X-PocketServer-Host header, or query param
        String targetHost = extractTargetHost(request);

        // 3. Health & Gateway Root Status endpoint
        if (isGatewayRoot(targetHost, uri)) {
            sendGatewayStatusResponse(ctx, request);
            return;
        }

        // 4. Resolve ActiveTunnel
        ActiveTunnel tunnel = tunnelRegistry.getTunnelByHost(targetHost);

        if (tunnel == null || !tunnel.isOpen()) {
            metrics.incrementNotFoundRequests();
            sendDeploymentOfflineResponse(ctx, request, targetHost);
            return;
        }

        // 5. Route to active Android tunnel via HttpChunkedStreamer
        chunkedStreamer.handleVisitorRequest(ctx, request, tunnel);
    }

    private String extractTargetHost(FullHttpRequest request) {
        // Priority 1: Developer override header
        String customHost = request.headers().get("X-PocketServer-Host");
        if (customHost != null && !customHost.trim().isEmpty()) {
            return customHost.trim();
        }

        // Priority 2: Developer query param (?_host=mysubdomain)
        String uri = request.uri();
        int queryIdx = uri.indexOf("?_host=");
        if (queryIdx >= 0) {
            String sub = uri.substring(queryIdx + 7);
            int amp = sub.indexOf('&');
            return amp > 0 ? sub.substring(0, amp) : sub;
        }

        // Priority 3: Standard Host header
        String hostHeader = request.headers().get(HttpHeaderNames.HOST);
        return hostHeader != null ? hostHeader.trim() : "";
    }

    private boolean isGatewayRoot(String host, String uri) {
        String base = config.getBaseDomain().toLowerCase(Locale.ROOT);
        String cleanHost = host.toLowerCase(Locale.ROOT);
        int colon = cleanHost.indexOf(':');
        if (colon > 0) {
            cleanHost = cleanHost.substring(0, colon);
        }

        boolean isBase = cleanHost.equals(base) || cleanHost.equals("localhost") || cleanHost.equals("127.0.0.1");
        return isBase && (uri.equals("/") || uri.equals("/health") || uri.equals("/status"));
    }

    private void sendGatewayStatusResponse(ChannelHandlerContext ctx, FullHttpRequest req) {
        String json = "{\n" +
                "  \"gateway\": \"PocketServer Cloud Relay\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"status\": \"UP\",\n" +
                "  \"activeTunnels\": " + tunnelRegistry.getActiveTunnelCount() + ",\n" +
                "  \"totalRequests\": " + metrics.getTotalVisitorRequests() + ",\n" +
                "  \"totalBytes\": " + metrics.getTotalBytesTransferred() + "\n" +
                "}";

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        writeResponse(ctx, req, response);
    }

    private void sendDeploymentOfflineResponse(ChannelHandlerContext ctx, FullHttpRequest req, String targetHost) {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>PocketServer - Deployment Offline</title>\n" +
                "  <style>\n" +
                "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "           background: #0f172a; color: #f8fafc; display: flex; align-items: center; " +
                "           justify-content: center; height: 100vh; margin: 0; }\n" +
                "    .card { background: #1e293b; padding: 2.5rem; border-radius: 1rem; " +
                "            box-shadow: 0 10px 25px rgba(0,0,0,0.5); max-width: 500px; text-align: center; border: 1px solid #334155; }\n" +
                "    h1 { color: #f43f5e; margin-top: 0; font-size: 1.75rem; }\n" +
                "    p { color: #94a3b8; line-height: 1.6; }\n" +
                "    .badge { display: inline-block; background: #334155; color: #38bdf8; padding: 0.25rem 0.75rem; " +
                "             border-radius: 0.5rem; font-family: monospace; margin: 0.5rem 0; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"card\">\n" +
                "    <h1>Deployment Offline</h1>\n" +
                "    <div class=\"badge\">" + (targetHost.isEmpty() ? "unknown" : targetHost) + "</div>\n" +
                "    <p>This PocketServer deployment is currently stopped or the Android device is offline.</p>\n" +
                "    <p>Launch the PocketServer app on the host device and start the deployment to make it available.</p>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        writeResponse(ctx, req, response);
    }

    private void writeResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (!keepAlive) {
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(res);
        }
    }
}
