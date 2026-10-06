package com.example.pocketserver.relay;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import com.example.pocketserver.relay.model.ActiveTunnel;
import com.example.pocketserver.relay.registry.TunnelRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TunnelRegistryTest {

    private GatewayConfig config;
    private GatewayMetrics metrics;
    private TunnelRegistry registry;

    @Before
    public void setUp() {
        config = new GatewayConfig()
                .setBaseDomain("pocketserver.dev")
                .setMaxActiveTunnelsPerDevice(3);
        metrics = new GatewayMetrics();
        registry = new TunnelRegistry(config, metrics);
    }

    private ChannelHandlerContext createMockContext() {
        EmbeddedChannel channel = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        return channel.pipeline().firstContext();
    }

    @Test
    public void testRegisterAndLookupByHost() {
        ChannelHandlerContext ctx = createMockContext();
        ActiveTunnel tunnel = registry.registerTunnel("dep-123", "proj-abc", "my-portfolio", ctx);

        assertNotNull(tunnel);
        assertEquals("my-portfolio", tunnel.getHostname());
        assertEquals("dep-123", tunnel.getDeploymentId());
        assertEquals(1, registry.getActiveTunnelCount());
        assertEquals(1, metrics.getActiveTunnels());

        // Lookup by exact hostname
        ActiveTunnel byHost = registry.getTunnelByHost("my-portfolio");
        assertNotNull(byHost);
        assertEquals("dep-123", byHost.getDeploymentId());

        // Lookup by FQDN
        ActiveTunnel byFqdn = registry.getTunnelByHost("my-portfolio.pocketserver.dev");
        assertNotNull(byFqdn);
        assertEquals("dep-123", byFqdn.getDeploymentId());

        // Lookup with port
        ActiveTunnel byPort = registry.getTunnelByHost("my-portfolio.pocketserver.dev:8080");
        assertNotNull(byPort);
        assertEquals("dep-123", byPort.getDeploymentId());
    }

    @Test
    public void testDeviceQuotaEnforcement() {
        ChannelHandlerContext ctx1 = createMockContext();
        ChannelHandlerContext ctx2 = createMockContext();
        ChannelHandlerContext ctx3 = createMockContext();
        ChannelHandlerContext ctx4 = createMockContext();

        registry.registerTunnel("dep-1", "proj-quota", "site1", ctx1);
        registry.registerTunnel("dep-2", "proj-quota", "site2", ctx2);
        registry.registerTunnel("dep-3", "proj-quota", "site3", ctx3);

        assertEquals(3, registry.getActiveTunnelCount());

        // 4th tunnel should throw IllegalStateException exceeding maxActiveTunnelsPerDevice (3)
        try {
            registry.registerTunnel("dep-4", "proj-quota", "site4", ctx4);
            fail("Expected quota exception for 4th tunnel");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Max active tunnels limit"));
        }
    }

    @Test
    public void testUnregisterTunnel() {
        ChannelHandlerContext ctx = createMockContext();
        registry.registerTunnel("dep-123", "proj-abc", "my-portfolio", ctx);
        assertEquals(1, registry.getActiveTunnelCount());

        ActiveTunnel unregistered = registry.unregisterTunnel(ctx);
        assertNotNull(unregistered);
        assertEquals(0, registry.getActiveTunnelCount());
        assertEquals(0, metrics.getActiveTunnels());

        assertNull(registry.getTunnelByHost("my-portfolio"));
        assertNull(registry.getTunnelByDeploymentId("dep-123"));
    }
}
