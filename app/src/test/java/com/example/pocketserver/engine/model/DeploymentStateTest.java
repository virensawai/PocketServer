package com.example.pocketserver.engine.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests verifying the formal PocketServer deployment state machine rules.
 */
public class DeploymentStateTest {

    @Test
    public void testValidTransitionsFromStopped() {
        assertTrue(DeploymentState.STOPPED.canTransitionTo(DeploymentState.STARTING));
        assertFalse(DeploymentState.STOPPED.canTransitionTo(DeploymentState.LIVE));
        assertFalse(DeploymentState.STOPPED.canTransitionTo(DeploymentState.CONNECTING));
        assertFalse(DeploymentState.STOPPED.canTransitionTo(DeploymentState.NETWORK_LOST));
    }

    @Test
    public void testValidTransitionsFromStarting() {
        assertTrue(DeploymentState.STARTING.canTransitionTo(DeploymentState.CONNECTING));
        assertTrue(DeploymentState.STARTING.canTransitionTo(DeploymentState.LIVE));
        assertTrue(DeploymentState.STARTING.canTransitionTo(DeploymentState.FAILED));
        assertTrue(DeploymentState.STARTING.canTransitionTo(DeploymentState.STOPPED));
        assertFalse(DeploymentState.STARTING.canTransitionTo(DeploymentState.RECONNECTING));
    }

    @Test
    public void testValidTransitionsFromConnecting() {
        assertTrue(DeploymentState.CONNECTING.canTransitionTo(DeploymentState.LIVE));
        assertTrue(DeploymentState.CONNECTING.canTransitionTo(DeploymentState.FAILED));
        assertTrue(DeploymentState.CONNECTING.canTransitionTo(DeploymentState.STOPPED));
        assertTrue(DeploymentState.CONNECTING.canTransitionTo(DeploymentState.NETWORK_LOST));
        assertFalse(DeploymentState.CONNECTING.canTransitionTo(DeploymentState.STARTING));
    }

    @Test
    public void testValidTransitionsFromLive() {
        assertTrue(DeploymentState.LIVE.canTransitionTo(DeploymentState.NETWORK_LOST));
        assertTrue(DeploymentState.LIVE.canTransitionTo(DeploymentState.STOPPED));
        assertTrue(DeploymentState.LIVE.canTransitionTo(DeploymentState.FAILED));
        assertFalse(DeploymentState.LIVE.canTransitionTo(DeploymentState.STARTING));
        assertFalse(DeploymentState.LIVE.canTransitionTo(DeploymentState.CONNECTING));
    }

    @Test
    public void testValidTransitionsFromNetworkLost() {
        assertTrue(DeploymentState.NETWORK_LOST.canTransitionTo(DeploymentState.RECONNECTING));
        assertTrue(DeploymentState.NETWORK_LOST.canTransitionTo(DeploymentState.STOPPED));
        assertTrue(DeploymentState.NETWORK_LOST.canTransitionTo(DeploymentState.FAILED));
        assertFalse(DeploymentState.NETWORK_LOST.canTransitionTo(DeploymentState.LIVE));
    }

    @Test
    public void testValidTransitionsFromReconnecting() {
        assertTrue(DeploymentState.RECONNECTING.canTransitionTo(DeploymentState.LIVE));
        assertTrue(DeploymentState.RECONNECTING.canTransitionTo(DeploymentState.NETWORK_LOST));
        assertTrue(DeploymentState.RECONNECTING.canTransitionTo(DeploymentState.FAILED));
        assertTrue(DeploymentState.RECONNECTING.canTransitionTo(DeploymentState.STOPPED));
        assertFalse(DeploymentState.RECONNECTING.canTransitionTo(DeploymentState.STARTING));
    }

    @Test
    public void testValidTransitionsFromFailed() {
        assertTrue(DeploymentState.FAILED.canTransitionTo(DeploymentState.STOPPED));
        assertTrue(DeploymentState.FAILED.canTransitionTo(DeploymentState.STARTING));
        assertFalse(DeploymentState.FAILED.canTransitionTo(DeploymentState.LIVE));
        assertFalse(DeploymentState.FAILED.canTransitionTo(DeploymentState.CONNECTING));
    }

    @Test
    public void testSelfTransitionAllowed() {
        for (DeploymentState state : DeploymentState.values()) {
            assertTrue(state.canTransitionTo(state));
        }
    }

    @Test
    public void testActiveAndRunningFlags() {
        assertFalse(DeploymentState.STOPPED.isActive());
        assertFalse(DeploymentState.FAILED.isActive());

        assertTrue(DeploymentState.STARTING.isActive());
        assertTrue(DeploymentState.CONNECTING.isActive());
        assertTrue(DeploymentState.LIVE.isActive());
        assertTrue(DeploymentState.NETWORK_LOST.isActive());
        assertTrue(DeploymentState.RECONNECTING.isActive());

        assertTrue(DeploymentState.LIVE.isRunning());
        assertFalse(DeploymentState.CONNECTING.isRunning());
        assertFalse(DeploymentState.STOPPED.isRunning());
    }
}
