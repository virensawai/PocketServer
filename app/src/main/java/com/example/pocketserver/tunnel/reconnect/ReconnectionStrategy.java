package com.example.pocketserver.tunnel.reconnect;

import java.util.Random;

/**
 * Exponential backoff algorithm with full random jitter to prevent thundering herd problems
 * during cloud gateway or mobile network reconnect events.
 */
public class ReconnectionStrategy {

    public static final long DEFAULT_INITIAL_BACKOFF_MS = 1000L;    // 1 second
    public static final long DEFAULT_MAX_BACKOFF_MS = 30_000L;       // 30 seconds
    public static final double DEFAULT_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER_FRACTION = 0.2;        // +/- 20%
    public static final int DEFAULT_MAX_RETRIES = 10;

    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final double multiplier;
    private final double jitterFraction;
    private final int maxRetries;
    private final Random random;

    private int attemptCount = 0;
    private long currentBackoffMs;

    public ReconnectionStrategy() {
        this(
                DEFAULT_INITIAL_BACKOFF_MS,
                DEFAULT_MAX_BACKOFF_MS,
                DEFAULT_MULTIPLIER,
                DEFAULT_JITTER_FRACTION,
                DEFAULT_MAX_RETRIES,
                new Random()
        );
    }

    public ReconnectionStrategy(
            long initialBackoffMs,
            long maxBackoffMs,
            double multiplier,
            double jitterFraction,
            int maxRetries,
            Random random) {
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.multiplier = multiplier;
        this.jitterFraction = jitterFraction;
        this.maxRetries = maxRetries;
        this.random = random != null ? random : new Random();
        this.currentBackoffMs = initialBackoffMs;
    }

    /**
     * Determines if additional reconnection attempts are allowed under configured retry caps.
     */
    public synchronized boolean canRetry() {
        return maxRetries < 0 || attemptCount < maxRetries;
    }

    /**
     * Computes the subsequent delay in milliseconds, applying exponential backoff and random jitter.
     */
    public synchronized long getNextDelayMs() {
        attemptCount++;

        long calculated = currentBackoffMs;

        // Advance backoff for next round
        long nextBase = (long) (currentBackoffMs * multiplier);
        currentBackoffMs = Math.min(maxBackoffMs, nextBase);

        // Apply jitter: +/- (jitterFraction * calculated)
        double jitterRange = calculated * jitterFraction;
        double jitterOffset = (random.nextDouble() * 2.0 - 1.0) * jitterRange; // between -range and +range

        long finalDelay = Math.round(calculated + jitterOffset);
        return Math.max(100L, Math.min(maxBackoffMs, finalDelay));
    }

    /**
     * Resets backoff state upon successful connection or user reset.
     */
    public synchronized void reset() {
        this.attemptCount = 0;
        this.currentBackoffMs = initialBackoffMs;
    }

    public synchronized int getAttemptCount() {
        return attemptCount;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
