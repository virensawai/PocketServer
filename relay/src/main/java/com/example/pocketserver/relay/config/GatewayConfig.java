package com.example.pocketserver.relay.config;

/**
 * Configuration options and operational limits for the Netty Cloud Relay Gateway.
 */
public class GatewayConfig {

    private int port = 8080;
    private String baseDomain = "localhost";
    private String tunnelPath = "/tunnel";
    private String publicScheme = "http";
    private int maxVisitorRequestsPerSecondPerIp = 20;
    private int maxConcurrentConnectionsPerIp = 5;
    private int maxConcurrentStreamsPerDeployment = 20;
    private int maxActiveTunnelsPerDevice = 3;
    private long requestTimeoutMs = 30_000L;
    private boolean enableCompression = true;
    private int maxContentLength = 65536;

    public GatewayConfig() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                this.port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }
        String envDomain = System.getenv("PUBLIC_BASE_DOMAIN");
        if (envDomain != null && !envDomain.isEmpty()) {
            this.baseDomain = envDomain;
        }
    }

    public int getPort() {
        return port;
    }

    public GatewayConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    public GatewayConfig setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
        return this;
    }

    public String getTunnelPath() {
        return tunnelPath;
    }

    public GatewayConfig setTunnelPath(String tunnelPath) {
        this.tunnelPath = tunnelPath;
        return this;
    }

    public String getPublicScheme() {
        return publicScheme;
    }

    public GatewayConfig setPublicScheme(String publicScheme) {
        this.publicScheme = publicScheme;
        return this;
    }

    public int getMaxVisitorRequestsPerSecondPerIp() {
        return maxVisitorRequestsPerSecondPerIp;
    }

    public GatewayConfig setMaxVisitorRequestsPerSecondPerIp(int maxVisitorRequestsPerSecondPerIp) {
        this.maxVisitorRequestsPerSecondPerIp = maxVisitorRequestsPerSecondPerIp;
        return this;
    }

    public int getMaxConcurrentConnectionsPerIp() {
        return maxConcurrentConnectionsPerIp;
    }

    public GatewayConfig setMaxConcurrentConnectionsPerIp(int maxConcurrentConnectionsPerIp) {
        this.maxConcurrentConnectionsPerIp = maxConcurrentConnectionsPerIp;
        return this;
    }

    public int getMaxConcurrentStreamsPerDeployment() {
        return maxConcurrentStreamsPerDeployment;
    }

    public GatewayConfig setMaxConcurrentStreamsPerDeployment(int maxConcurrentStreamsPerDeployment) {
        this.maxConcurrentStreamsPerDeployment = maxConcurrentStreamsPerDeployment;
        return this;
    }

    public int getMaxActiveTunnelsPerDevice() {
        return maxActiveTunnelsPerDevice;
    }

    public GatewayConfig setMaxActiveTunnelsPerDevice(int maxActiveTunnelsPerDevice) {
        this.maxActiveTunnelsPerDevice = maxActiveTunnelsPerDevice;
        return this;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public GatewayConfig setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
        return this;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public GatewayConfig setEnableCompression(boolean enableCompression) {
        this.enableCompression = enableCompression;
        return this;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public GatewayConfig setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }
}
