package com.example.pocketserver.relay;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.handler.GatewayCompressionHandler;
import com.example.pocketserver.relay.handler.HttpChunkedStreamer;
import com.example.pocketserver.relay.handler.RateLimiterHandler;
import com.example.pocketserver.relay.handler.WebSocketTunnelHandler;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import com.example.pocketserver.relay.registry.TunnelRegistry;
import com.example.pocketserver.relay.router.SubdomainRouter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Production Java Netty Data Plane Gateway.
 * Terminates HTTP traffic, enforces host routing, rate limiting, content compression,
 * and coordinates multiplexed binary streaming with Android reverse tunnels.
 */
public class NettyGatewayServer {

    private static final Logger LOGGER = Logger.getLogger(NettyGatewayServer.class.getName());

    private final GatewayConfig config;
    private final GatewayMetrics metrics;
    private final TunnelRegistry tunnelRegistry;
    private final HttpChunkedStreamer chunkedStreamer;
    private final RateLimiterHandler rateLimiterHandler;
    private final SubdomainRouter subdomainRouter;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean isRunning = false;

    public NettyGatewayServer() {
        this(new GatewayConfig());
    }

    public NettyGatewayServer(GatewayConfig config) {
        this.config = config;
        this.metrics = new GatewayMetrics();
        this.tunnelRegistry = new TunnelRegistry(config, metrics);
        this.chunkedStreamer = new HttpChunkedStreamer(config, metrics);
        this.rateLimiterHandler = new RateLimiterHandler(config, metrics);
        this.subdomainRouter = new SubdomainRouter(config, tunnelRegistry, chunkedStreamer, metrics);
    }

    /**
     * Starts the Netty Gateway server and binds to the configured port.
     */
    public synchronized void start() throws InterruptedException {
        if (isRunning) {
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 1. HTTP Server Codec (Request Decoder & Response Encoder)
                        pipeline.addLast("http-codec", new HttpServerCodec());

                        // 2. Gateway Compression (Selectively Gzips text and skips media)
                        if (config.isEnableCompression()) {
                            pipeline.addLast("compressor", new GatewayCompressionHandler());
                        }

                        // 3. HTTP Aggregator (for WebSocket handshake and small requests)
                        pipeline.addLast("aggregator", new HttpObjectAggregator(config.getMaxContentLength()));

                        // 4. Rate Limiter (Visitor IP tier limits)
                        pipeline.addLast("rate-limiter", rateLimiterHandler);

                        // 5. Subdomain & Host Router
                        pipeline.addLast("subdomain-router", subdomainRouter);

                        // 6. WebSocket Tunnel Endpoint Handler
                        pipeline.addLast("tunnel-handler", new WebSocketTunnelHandler(config, tunnelRegistry, chunkedStreamer));
                    }
                });

        serverChannel = bootstrap.bind(config.getPort()).sync().channel();
        int actualPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        config.setPort(actualPort);
        isRunning = true;

        LOGGER.info("=================================================");
        LOGGER.info(" PocketServer Cloud Relay Gateway LIVE on port: " + actualPort);
        LOGGER.info(" Base Domain: " + config.getBaseDomain());
        LOGGER.info(" Tunnel Endpoint: " + config.getTunnelPath());
        LOGGER.info("=================================================");
    }

    /**
     * Gracefully stops the Netty Gateway server.
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        LOGGER.info("Shutting down PocketServer Netty Gateway...");
        isRunning = false;

        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }

        tunnelRegistry.clear();
        LOGGER.info("PocketServer Netty Gateway stopped.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return config.getPort();
    }

    public GatewayConfig getConfig() {
        return config;
    }

    public GatewayMetrics getMetrics() {
        return metrics;
    }

    public TunnelRegistry getTunnelRegistry() {
        return tunnelRegistry;
    }

    public HttpChunkedStreamer getChunkedStreamer() {
        return chunkedStreamer;
    }

    /**
     * Standalone executable entry point.
     */
    public static void main(String[] args) {
        GatewayConfig config = new GatewayConfig();
        if (args.length > 0) {
            try {
                config.setPort(Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {}
        }
        if (args.length > 1) {
            config.setBaseDomain(args[1]);
        }

        NettyGatewayServer server = new NettyGatewayServer(config);
        try {
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            // Wait until channel closes
            server.serverChannel.closeFuture().sync();
        } catch (Exception e) {
            LOGGER.severe("Fatal error running NettyGatewayServer: " + e.getMessage());
            System.exit(1);
        } finally {
            server.stop();
        }
    }
}
