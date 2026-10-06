package com.example.pocketserver.relay.model;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Encapsulates an active outbound WebSocket reverse tunnel from an Android device.
 * Stored in TunnelRegistry in-memory maps for zero-DB request routing.
 */
public class ActiveTunnel {

    private final String deploymentId;
    private final String projectId;
    private final String hostname;
    private final ChannelHandlerContext channelContext;
    private final long connectedAt;

    private final AtomicInteger activeStreamCount = new AtomicInteger(0);
    private final LongAdder totalRequestsServed = new LongAdder();
    private final LongAdder totalBytesStreamed = new LongAdder();

    public ActiveTunnel(
            String deploymentId,
            String projectId,
            String hostname,
            ChannelHandlerContext channelContext) {
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.hostname = hostname;
        this.channelContext = channelContext;
        this.connectedAt = System.currentTimeMillis();
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getHostname() {
        return hostname;
    }

    public ChannelHandlerContext getChannelContext() {
        return channelContext;
    }

    public Channel getChannel() {
        return channelContext != null ? channelContext.channel() : null;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public int getActiveStreamCount() {
        return activeStreamCount.get();
    }

    public int incrementActiveStreamCount() {
        return activeStreamCount.incrementAndGet();
    }

    public int decrementActiveStreamCount() {
        return activeStreamCount.updateAndGet(count -> Math.max(0, count - 1));
    }

    public void incrementRequestsServed() {
        totalRequestsServed.increment();
    }

    public void addBytesStreamed(long bytes) {
        if (bytes > 0) {
            totalBytesStreamed.add(bytes);
        }
    }

    public long getTotalRequestsServed() {
        return totalRequestsServed.sum();
    }

    public long getTotalBytesStreamed() {
        return totalBytesStreamed.sum();
    }

    public boolean isOpen() {
        return channelContext != null && channelContext.channel().isActive();
    }

    /**
     * Sends a JSON control frame to the Android client over WebSocket.
     */
    public void sendControlFrame(String json) {
        if (isOpen()) {
            channelContext.channel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }
}
