package com.example.pocketserver.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for DeploymentTelemetry models and formatted metric representations.
 */
public class DeploymentTelemetryTest {

    @Test
    public void testDefaultTelemetry() {
        DeploymentTelemetry telemetry = new DeploymentTelemetry();
        assertEquals(0, telemetry.getRequestCount());
        assertEquals(0, telemetry.getBytesTransferred());
        assertEquals(0, telemetry.getActiveStreams());
        assertEquals("0 B", telemetry.getFormattedBytes());
        assertEquals("00:00:00", telemetry.getFormattedUptime());
    }

    @Test
    public void testFormatBytes() {
        assertEquals("500 B", DeploymentTelemetry.formatBytes(500));
        assertEquals("1.0 KB", DeploymentTelemetry.formatBytes(1024));
        assertEquals("2.5 MB", DeploymentTelemetry.formatBytes((long) (2.5 * 1024 * 1024)));
        assertEquals("1.0 GB", DeploymentTelemetry.formatBytes(1024L * 1024 * 1024));
    }

    @Test
    public void testWithAddedRequest() {
        DeploymentTelemetry initial = new DeploymentTelemetry();
        DeploymentTelemetry updated = initial.withAddedRequest(1024);

        assertEquals(1, updated.getRequestCount());
        assertEquals(1024, updated.getBytesTransferred());
        assertTrue(updated.getStartedAtTimestamp() > 0);
        assertTrue(updated.getLastRequestTimestamp() > 0);
    }

    @Test
    public void testWithActiveStreams() {
        DeploymentTelemetry initial = new DeploymentTelemetry();
        DeploymentTelemetry updated = initial.withActiveStreams(5);
        assertEquals(5, updated.getActiveStreams());

        // Negative values clamp to 0
        DeploymentTelemetry clamped = updated.withActiveStreams(-2);
        assertEquals(0, clamped.getActiveStreams());
    }
}
