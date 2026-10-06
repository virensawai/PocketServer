package com.example.pocketserver.control.api;

import com.example.pocketserver.control.repository.ControlPlaneRepository;
import com.example.pocketserver.control.service.AuthService;
import com.example.pocketserver.control.service.DeploymentService;
import com.example.pocketserver.control.service.DeviceService;
import com.example.pocketserver.control.service.HostnameAssignmentService;
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
 * Netty server hosting the PocketServer Control Plane REST API.
 */
public class ControlPlaneServer {

    private static final Logger LOGGER = Logger.getLogger(ControlPlaneServer.class.getName());

    private int port;
    private final ControlPlaneRepository repository;
    private final AuthService authService;
    private final DeviceService deviceService;
    private final HostnameAssignmentService hostnameService;
    private final DeploymentService deploymentService;
    private final ControlPlaneRestHandler restHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean isRunning = false;

    public ControlPlaneServer() {
        this(8081);
    }

    public ControlPlaneServer(int port) {
        this.port = port;
        this.repository = new ControlPlaneRepository();
        this.authService = new AuthService(repository);
        this.deviceService = new DeviceService(repository);
        this.hostnameService = new HostnameAssignmentService(repository);
        this.deploymentService = new DeploymentService(repository, hostnameService);
        this.restHandler = new ControlPlaneRestHandler(authService, deviceService, deploymentService);
    }

    public synchronized void start() throws InterruptedException {
        if (isRunning) {
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 512)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(1024 * 1024)); // 1 MB JSON body limit
                        pipeline.addLast("rest-handler", restHandler);
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        this.port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        isRunning = true;

        LOGGER.info("PocketServer Control Plane REST API LIVE on port: " + this.port);
    }

    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

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
        LOGGER.info("PocketServer Control Plane REST API stopped.");
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public ControlPlaneRepository getRepository() {
        return repository;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public DeviceService getDeviceService() {
        return deviceService;
    }

    public DeploymentService getDeploymentService() {
        return deploymentService;
    }

    public static void main(String[] args) {
        int port = 8081;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        ControlPlaneServer server = new ControlPlaneServer(port);
        try {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.serverChannel.closeFuture().sync();
        } catch (Exception e) {
            LOGGER.severe("ControlPlaneServer failed: " + e.getMessage());
            System.exit(1);
        } finally {
            server.stop();
        }
    }
}
