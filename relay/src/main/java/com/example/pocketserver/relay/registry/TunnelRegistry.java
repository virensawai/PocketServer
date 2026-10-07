package com.example.pocketserver.relay.registry;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import com.example.pocketserver.relay.model.ActiveTunnel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory reverse tunnel registry.
 * Provides O(1) routing lookups on the hot request path with zero database queries.
 */
public class TunnelRegistry {

    private final GatewayConfig config;
    private final GatewayMetrics metrics;

    // Fast routing maps
    private final ConcurrentHashMap<String, ActiveTunnel> hostToTunnelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ActiveTunnel> deploymentToTunnelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChannelId, ActiveTunnel> channelIdToTunnelMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> projectToDeploymentsMap = new ConcurrentHashMap<>();

    public TunnelRegistry(GatewayConfig config, GatewayMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Registers an inbound Android reverse tunnel connection.
     *
     * @param deploymentId      Deployment ID
     * @param projectId         Project or Device ID
     * @param requestedHostname Optional requested subdomain/hostname
     * @param ctx               Netty ChannelHandlerContext of the WebSocket channel
     * @return Registered ActiveTunnel
     * @throws IllegalStateException if device/project quota is exceeded
     */
    public ActiveTunnel registerTunnel(
            String deploymentId,
            String projectId,
            String requestedHostname,
            ChannelHandlerContext ctx) {
        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("deploymentId must not be empty");
        }
        String safeProjectId = (projectId != null && !projectId.trim().isEmpty()) ? projectId.trim() : "default";

        // Enforce Device/Project Tier Limit (e.g. max 3 active tunnels)
        Set<String> projectDeployments = projectToDeploymentsMap.computeIfAbsent(
                safeProjectId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );

        if (!projectDeployments.contains(deploymentId) &&
                projectDeployments.size() >= config.getMaxActiveTunnelsPerDevice()) {
            throw new IllegalStateException("Max active tunnels limit (" +
                    config.getMaxActiveTunnelsPerDevice() + ") reached for project: " + safeProjectId);
        }

        // Clean up any stale registration for the same deploymentId
        ActiveTunnel existing = deploymentToTunnelMap.get(deploymentId);
        if (existing != null) {
            unregisterTunnel(existing.getChannelContext());
        }

        // Resolve clean subdomain/hostname
        String assignedSubdomain = resolveSubdomain(deploymentId, requestedHostname);

        ActiveTunnel tunnel = new ActiveTunnel(deploymentId, safeProjectId, assignedSubdomain, ctx);

        hostToTunnelMap.put(assignedSubdomain.toLowerCase(Locale.ROOT), tunnel);
        deploymentToTunnelMap.put(deploymentId, tunnel);
        channelIdToTunnelMap.put(ctx.channel().id(), tunnel);
        projectDeployments.add(deploymentId);

        metrics.incrementActiveTunnels();
        return tunnel;
    }

    /**
     * Unregisters a tunnel by its Netty ChannelHandlerContext.
     */
    public ActiveTunnel unregisterTunnel(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }
        Channel channel = ctx.channel();
        ActiveTunnel tunnel = channelIdToTunnelMap.remove(channel.id());
        if (tunnel != null) {
            deploymentToTunnelMap.remove(tunnel.getDeploymentId());
            hostToTunnelMap.remove(tunnel.getHostname().toLowerCase(Locale.ROOT));

            Set<String> deployments = projectToDeploymentsMap.get(tunnel.getProjectId());
            if (deployments != null) {
                deployments.remove(tunnel.getDeploymentId());
                if (deployments.isEmpty()) {
                    projectToDeploymentsMap.remove(tunnel.getProjectId());
                }
            }
            metrics.decrementActiveTunnels();
        }
        return tunnel;
    }

    /**
     * Resolves an ActiveTunnel by Host header or subdomain string.
     */
    public ActiveTunnel getTunnelByHost(String rawHost) {
        if (rawHost == null || rawHost.trim().isEmpty()) {
            return null;
        }

        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        // Strip port if present (e.g., "myhost.localhost:8080" -> "myhost.localhost")
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) {
            host = host.substring(0, colonIdx);
        }

        // 1. Direct match
        ActiveTunnel tunnel = hostToTunnelMap.get(host);
        if (tunnel != null && tunnel.isOpen()) {
            return tunnel;
        }

        // 2. Extract subdomain prefix if host ends with baseDomain
        String baseDomain = config.getBaseDomain().toLowerCase(Locale.ROOT);
        if (host.endsWith("." + baseDomain)) {
            String subdomain = host.substring(0, host.length() - baseDomain.length() - 1);
            tunnel = hostToTunnelMap.get(subdomain);
            if (tunnel != null && tunnel.isOpen()) {
                return tunnel;
            }
        }

        // 3. Fallback: check if host equals deploymentId
        tunnel = deploymentToTunnelMap.get(host);
        if (tunnel != null && tunnel.isOpen()) {
            return tunnel;
        }

        // 4. Fallback: if single active tunnel is registered, route directly to it
        if (channelIdToTunnelMap.size() == 1) {
            ActiveTunnel single = channelIdToTunnelMap.values().iterator().next();
            if (single != null && single.isOpen()) {
                return single;
            }
        }

        return null;
    }

    public ActiveTunnel getTunnelByDeploymentId(String deploymentId) {
        if (deploymentId == null) {
            return null;
        }
        ActiveTunnel tunnel = deploymentToTunnelMap.get(deploymentId);
        return (tunnel != null && tunnel.isOpen()) ? tunnel : null;
    }

    public ActiveTunnel getTunnelByChannel(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }
        return channelIdToTunnelMap.get(ctx.channel().id());
    }

    public int getActiveTunnelCount() {
        return deploymentToTunnelMap.size();
    }

    public Map<String, ActiveTunnel> getHostToTunnelMap() {
        return Collections.unmodifiableMap(hostToTunnelMap);
    }

    public void clear() {
        hostToTunnelMap.clear();
        deploymentToTunnelMap.clear();
        channelIdToTunnelMap.clear();
        projectToDeploymentsMap.clear();
    }

    private String resolveSubdomain(String deploymentId, String requestedHostname) {
        if (requestedHostname != null && !requestedHostname.trim().isEmpty()) {
            String sanitized = sanitizeSubdomain(requestedHostname);
            if (!sanitized.isEmpty()) {
                // If already claimed by another active deployment, append suffix
                if (!hostToTunnelMap.containsKey(sanitized)) {
                    return sanitized;
                }
                String candidate = sanitized + "-" + sanitizeSubdomain(deploymentId);
                if (!hostToTunnelMap.containsKey(candidate)) {
                    return candidate;
                }
            }
        }

        String safeDepId = sanitizeSubdomain(deploymentId);
        if (!safeDepId.isEmpty()) {
            return safeDepId;
        }

        return "tunnel-" + System.currentTimeMillis() % 100000;
    }

    private static String sanitizeSubdomain(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "");
    }
}
