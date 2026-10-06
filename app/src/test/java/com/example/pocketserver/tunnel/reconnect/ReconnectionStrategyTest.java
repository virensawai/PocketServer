package com.example.pocketserver.tunnel.reconnect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

/**
 * Unit tests for ReconnectionStrategy exponential backoff, jitter, and retry limits.
 */
public class ReconnectionStrategyTest {

    @Test
    public void testExponentialBackoffProgression() {
        // Zero jitter random generator for deterministic testing
        Random zeroJitterRandom = new Random() {
            @Override
            public double nextDouble() {
                return 0.5; // (0.5 * 2.0 - 1.0) = 0 offset
            }
        };

        ReconnectionStrategy strategy = new ReconnectionStrategy(
                1000L,
                8000L,
                2.0,
                0.0,
                3,
                zeroJitterRandom
        );

        assertTrue(strategy.canRetry());
        assertEquals(0, strategy.getAttemptCount());

        long d1 = strategy.getNextDelayMs();
        assertEquals(1, strategy.getAttemptCount());
        assertEquals(1000L, d1);

        long d2 = strategy.getNextDelayMs();
        assertEquals(2, strategy.getAttemptCount());
        assertEquals(2000L, d2);

        long d3 = strategy.getNextDelayMs();
        assertEquals(3, strategy.getAttemptCount());
        assertEquals(4000L, d3);

        assertFalse(strategy.canRetry()); // Reached max retries (3)
    }

    @Test
    public void testMaxBackoffCap() {
        ReconnectionStrategy strategy = new ReconnectionStrategy(
                5000L,
                10000L,
                3.0,
                0.0,
                10,
                new Random()
        );

        long d1 = strategy.getNextDelayMs(); // 5000
        long d2 = strategy.getNextDelayMs(); // 15000 capped at 10000
        assertTrue(d1 <= 10000L);
        assertEquals(10000L, d2);
    }

    @Test
    public void testResetRestoresInitialState() {
        ReconnectionStrategy strategy = new ReconnectionStrategy();
        strategy.getNextDelayMs();
        strategy.getNextDelayMs();
        assertEquals(2, strategy.getAttemptCount());

        strategy.reset();
        assertEquals(0, strategy.getAttemptCount());
        assertTrue(strategy.canRetry());
    }
}
