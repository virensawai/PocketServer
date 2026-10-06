package com.example.pocketserver.engine.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for DataUsageTracker guardrails and threshold triggering.
 */
public class DataUsageTrackerTest {

    private DataUsageTracker tracker;
    private final long warningThreshold = 1000L;
    private final long limitThreshold = 2000L;

    private final AtomicInteger warningCount = new AtomicInteger(0);
    private final AtomicInteger limitCount = new AtomicInteger(0);

    @Before
    public void setUp() {
        tracker = new DataUsageTracker(warningThreshold, limitThreshold);
        warningCount.set(0);
        limitCount.set(0);

        tracker.setListener(new DataUsageTracker.DataUsageListener() {
            @Override
            public void onWarningThresholdReached(long bytesTransferred, long warning) {
                warningCount.incrementAndGet();
            }

            @Override
            public void onLimitThresholdExceeded(long bytesTransferred, long limit) {
                limitCount.incrementAndGet();
            }
        });
    }

    @Test
    public void testInitialState() {
        assertEquals(0, tracker.getBytesTransferred());
        assertFalse(tracker.isWarningFired());
        assertFalse(tracker.isLimitFired());
    }

    @Test
    public void testBelowWarningThreshold() {
        boolean limitExceeded = tracker.addBytes(500);
        assertFalse(limitExceeded);
        assertEquals(500, tracker.getBytesTransferred());
        assertEquals(0, warningCount.get());
        assertEquals(0, limitCount.get());
    }

    @Test
    public void testWarningThresholdTriggeredOnce() {
        tracker.addBytes(1000);
        assertEquals(1, warningCount.get());
        assertEquals(0, limitCount.get());
        assertTrue(tracker.isWarningFired());

        // Subsequent bytes should NOT fire warning again
        tracker.addBytes(500);
        assertEquals(1, warningCount.get());
        assertEquals(0, limitCount.get());
    }

    @Test
    public void testLimitThresholdTriggeredOnce() {
        tracker.addBytes(1500);
        assertEquals(1, warningCount.get());
        assertEquals(0, limitCount.get());

        boolean limitExceeded = tracker.addBytes(600); // Total: 2100 >= 2000
        assertTrue(limitExceeded);
        assertEquals(1, limitCount.get());
        assertTrue(tracker.isLimitFired());

        // Further additions keep limit true but don't re-fire callback
        tracker.addBytes(500);
        assertEquals(1, limitCount.get());
    }

    @Test
    public void testResetClearsAndRearms() {
        tracker.addBytes(2500);
        assertEquals(1, warningCount.get());
        assertEquals(1, limitCount.get());

        tracker.reset();
        assertEquals(0, tracker.getBytesTransferred());
        assertFalse(tracker.isWarningFired());
        assertFalse(tracker.isLimitFired());

        // Can re-trigger after reset
        tracker.addBytes(1050);
        assertEquals(2, warningCount.get());
    }
}
