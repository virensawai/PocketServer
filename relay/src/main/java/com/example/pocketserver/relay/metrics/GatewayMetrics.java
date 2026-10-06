package com.example.pocketserver.relay.metrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe operational metrics tracking traffic, concurrency, and error rates in Netty Gateway.
 */
public class GatewayMetrics {

    private final LongAdder totalVisitorRequests = new LongAdder();
    private final LongAdder totalBytesTransferred = new LongAdder();
    private final AtomicInteger activeVisitorRequests = new AtomicInteger(0);
    private final AtomicInteger activeTunnels = new AtomicInteger(0);
    private final LongAdder rateLimitedRequests = new LongAdder();
    private final LongAdder notFoundRequests = new LongAdder();
    private final LongAdder timeoutRequests = new LongAdder();
    private final LongAdder cancelledRequests = new LongAdder();

    public void incrementTotalRequests() {
        totalVisitorRequests.increment();
    }

    public void recordBytesTransferred(long bytes) {
        if (bytes > 0) {
            totalBytesTransferred.add(bytes);
        }
    }

    public void incrementActiveRequests() {
        activeVisitorRequests.incrementAndGet();
    }

    public void decrementActiveRequests() {
        activeVisitorRequests.decrementAndGet();
    }

    public void incrementActiveTunnels() {
        activeTunnels.incrementAndGet();
    }

    public void decrementActiveTunnels() {
        activeTunnels.decrementAndGet();
    }

    public void incrementRateLimitedRequests() {
        rateLimitedRequests.increment();
    }

    public void incrementNotFoundRequests() {
        notFoundRequests.increment();
    }

    public void incrementTimeoutRequests() {
        timeoutRequests.increment();
    }

    public void incrementCancelledRequests() {
        cancelledRequests.increment();
    }

    // --- Getters ---

    public long getTotalVisitorRequests() {
        return totalVisitorRequests.sum();
    }

    public long getTotalBytesTransferred() {
        return totalBytesTransferred.sum();
    }

    public int getActiveVisitorRequests() {
        return activeVisitorRequests.get();
    }

    public int getActiveTunnels() {
        return activeTunnels.get();
    }

    public long getRateLimitedRequests() {
        return rateLimitedRequests.sum();
    }

    public long getNotFoundRequests() {
        return notFoundRequests.sum();
    }

    public long getTimeoutRequests() {
        return timeoutRequests.sum();
    }

    public long getCancelledRequests() {
        return cancelledRequests.sum();
    }

    public void reset() {
        totalVisitorRequests.reset();
        totalBytesTransferred.reset();
        activeVisitorRequests.set(0);
        activeTunnels.set(0);
        rateLimitedRequests.reset();
        notFoundRequests.reset();
        timeoutRequests.reset();
        cancelledRequests.reset();
    }
}
