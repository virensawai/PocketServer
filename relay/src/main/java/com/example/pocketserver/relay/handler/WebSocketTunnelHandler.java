package com.example.pocketserver.relay.handler;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.model.ActiveTunnel;
import com.example.pocketserver.relay.protocol.GatewayControlFrame;
import com.example.pocketserver.relay.protocol.GatewayFrameType;
import com.example.pocketserver.relay.registry.TunnelRegistry;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles reverse tunnel WebSocket connection lifecycle, registration handshakes,
 * and dispatching control/binary frames to and from Android devices.
 */
public class WebSocketTunnelHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = Logger.getLogger(WebSocketTunnelHandler.class.getName());

    private final GatewayConfig config;
    private final TunnelRegistry tunnelRegistry;
    private final HttpChunkedStreamer chunkedStreamer;

    private WebSocketServerHandshaker handshaker;
    private String handshakeDeploymentId;
    private String handshakeProjectId;

    public WebSocketTunnelHandler(
            GatewayConfig config,
            TunnelRegistry tunnelRegistry,
            HttpChunkedStreamer chunkedStreamer) {
        this.config = config;
        this.tunnelRegistry = tunnelRegistry;
        this.chunkedStreamer = chunkedStreamer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            ctx.channel().close();
            return;
        }

        this.handshakeDeploymentId = req.headers().get("X-Deployment-Id");
        this.handshakeProjectId = req.headers().get("X-Project-Id");

        String wsLocation = getWebSocketLocation(req);
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                wsLocation, null, true, config.getMaxContentLength()
        );

        this.handshaker = wsFactory.newHandshaker(req);
        if (this.handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            this.handshaker.handshake(ctx.channel(), req);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 1. Close Frame
        if (frame instanceof CloseWebSocketFrame) {
            if (handshaker != null) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            }
            return;
        }

        // 2. Ping / Pong Frames
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            return;
        }

        // 3. Binary Frame: Zero-allocation body streaming from Android to Visitor
        if (frame instanceof BinaryWebSocketFrame) {
            chunkedStreamer.handleResponseBody(frame.content());
            return;
        }

        // 4. Text Frame: Control Frames (REGISTER, RESPONSE_START, STREAM_END, PING, etc.)
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            GatewayControlFrame controlFrame = GatewayControlFrame.fromJson(text);
            if (controlFrame == null || controlFrame.getType() == null) {
                LOGGER.warning("Malformed tunnel control frame received: " + text);
                return;
            }

            processControlFrame(ctx, controlFrame);
        }
    }

    private void processControlFrame(ChannelHandlerContext ctx, GatewayControlFrame frame) {
        GatewayFrameType type = frame.getType();

        switch (type) {
            case REGISTER:
                handleRegister(ctx, frame);
                break;

            case RESPONSE_START:
                chunkedStreamer.handleResponseStart(frame);
                break;

            case STREAM_END:
                chunkedStreamer.handleStreamEnd(frame.getRequestId());
                break;

            case PING:
                ctx.channel().writeAndFlush(new TextWebSocketFrame(GatewayControlFrame.pong().toJson()));
                break;

            case PONG:
                // Heartbeat response acknowledged
                break;

            case ERROR:
                LOGGER.warning("Tunnel error from device: [" + frame.getErrorCode() + "] " + frame.getErrorMessage());
                break;

            default:
                LOGGER.fine("Unhandled frame type in WebSocket handler: " + type);
                break;
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, GatewayControlFrame frame) {
        String depId = frame.getDeploymentId() != null ? frame.getDeploymentId() : this.handshakeDeploymentId;
        String projId = frame.getProjectId() != null ? frame.getProjectId() : this.handshakeProjectId;

        if (depId == null || depId.trim().isEmpty()) {
            GatewayControlFrame err = GatewayControlFrame.error(null, 400, "Missing deploymentId in registration");
            ctx.channel().writeAndFlush(new TextWebSocketFrame(err.toJson()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        try {
            ActiveTunnel tunnel = tunnelRegistry.registerTunnel(
                    depId,
                    projId,
                    frame.getHostname(),
                    ctx
            );

            // Construct public URL
            String portSuffix = (config.getPort() != 80 && config.getPort() != 443) ? ":" + config.getPort() : "";
            String publicUrl = config.getPublicScheme() + "://" + tunnel.getHostname() + "." +
                    config.getBaseDomain() + portSuffix;

            GatewayControlFrame okFrame = GatewayControlFrame.registerOk(publicUrl, tunnel.getHostname());
            ctx.channel().writeAndFlush(new TextWebSocketFrame(okFrame.toJson()));

            LOGGER.info("Tunnel registered successfully: " + tunnel.getHostname() + " -> " + publicUrl);
        } catch (IllegalStateException e) {
            GatewayControlFrame err = GatewayControlFrame.error(null, 429, e.getMessage());
            ctx.channel().writeAndFlush(new TextWebSocketFrame(err.toJson()))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ActiveTunnel tunnel = tunnelRegistry.unregisterTunnel(ctx);
        if (tunnel != null) {
            LOGGER.info("Tunnel disconnected for deployment: " + tunnel.getDeploymentId() + " (" + tunnel.getHostname() + ")");
            chunkedStreamer.cancelAllForTunnel(tunnel);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.log(Level.WARNING, "WebSocketTunnelHandler exception", cause);
        ctx.close();
    }

    private String getWebSocketLocation(FullHttpRequest req) {
        String host = req.headers().get(HttpHeaderNames.HOST);
        String scheme = "ws://";
        return scheme + (host != null ? host : "localhost") + config.getTunnelPath();
    }
}
