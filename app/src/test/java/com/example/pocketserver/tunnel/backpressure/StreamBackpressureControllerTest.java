package com.example.pocketserver.tunnel.backpressure;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for StreamBackpressureController flow control logic.
 */
public class StreamBackpressureControllerTest {

    private StreamBackpressureController controller;
    private final long highWaterMark = 256 * 1024L;
    private final long lowWaterMark = 64 * 1024L;

    @Before
    public void setUp() {
        controller = new StreamBackpressureController(highWaterMark, lowWaterMark, 5L);
    }

    @Test
    public void testIsCongested() {
        assertFalse(controller.isCongested(100 * 1024L));
        assertFalse(controller.isCongested(255 * 1024L));
        assertTrue(controller.isCongested(256 * 1024L));
        assertTrue(controller.isCongested(300 * 1024L));
    }

    @Test
    public void testAwaitDrainReturnsImmediatelyWhenNotCongested() throws InterruptedException {
        QueueSizeProvider provider = () -> 32 * 1024L; // Below low water mark
        boolean drained = controller.awaitDrain(provider, new AtomicBoolean(false), 1000L);
        assertTrue(drained);
    }

    @Test
    public void testAwaitDrainSuccessAfterQueueReduction() throws InterruptedException {
        AtomicLong queue = new AtomicLong(300 * 1024L);
        QueueSizeProvider provider = queue::get;

        // Drain the queue asynchronously after 20ms
        new Thread(() -> {
            try {
                Thread.sleep(20);
                queue.set(50 * 1024L); // below low water mark
            } catch (InterruptedException ignored) {
            }
        }).start();

        boolean drained = controller.awaitDrain(provider, new AtomicBoolean(false), 1000L);
        assertTrue(drained);
    }

    @Test
    public void testAwaitDrainAbortsImmediatelyOnCancellation() throws InterruptedException {
        QueueSizeProvider provider = () -> 300 * 1024L;
        AtomicBoolean cancelled = new AtomicBoolean(true); // already cancelled

        boolean drained = controller.awaitDrain(provider, cancelled, 1000L);
        assertFalse(drained);
    }

    @Test
    public void testAwaitDrainTimesOutWhenQueueNeverDrains() throws InterruptedException {
        QueueSizeProvider provider = () -> 300 * 1024L; // constantly high
        boolean drained = controller.awaitDrain(provider, new AtomicBoolean(false), 50L);
        assertFalse(drained);
    }
}
