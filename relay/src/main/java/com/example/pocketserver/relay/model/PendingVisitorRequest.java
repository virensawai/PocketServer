package com.example.pocketserver.relay.model;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks an in-flight visitor HTTP request awaiting or receiving streamed chunks from an Android tunnel.
 */
public class PendingVisitorRequest {

    private final UUID requestId;
    private final ChannelHandlerContext visitorCtx;
    private final ActiveTunnel tunnel;
    private final boolean keepAlive;
    private final long startedAt;

    private final AtomicBoolean headersSent = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> timeoutFuture;

    public PendingVisitorRequest(
            UUID requestId,
            ChannelHandlerContext visitorCtx,
            ActiveTunnel tunnel,
            boolean keepAlive) {
        this.requestId = requestId;
        this.visitorCtx = visitorCtx;
        this.tunnel = tunnel;
        this.keepAlive = keepAlive;
        this.startedAt = System.currentTimeMillis();
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ChannelHandlerContext getVisitorCtx() {
        return visitorCtx;
    }

    public ActiveTunnel getTunnel() {
        return tunnel;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public boolean areHeadersSent() {
        return headersSent.get();
    }

    public boolean markHeadersSent() {
        return headersSent.compareAndSet(false, true);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean markCompleted() {
        boolean transitioned = completed.compareAndSet(false, true);
        if (transitioned) {
            cancelTimeout();
        }
        return transitioned;
    }

    public void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
        this.timeoutFuture = timeoutFuture;
    }

    public void cancelTimeout() {
        ScheduledFuture<?> future = this.timeoutFuture;
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }
}
