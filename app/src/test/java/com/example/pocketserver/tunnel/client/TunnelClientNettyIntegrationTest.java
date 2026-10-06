package com.example.pocketserver.tunnel.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import com.example.pocketserver.relay.NettyGatewayServer;
import com.example.pocketserver.relay.config.GatewayConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end integration test bridging Android's TunnelClient (Phase 3)
 * with the Java Netty Cloud Relay Data Plane (Phase 4).
 */
public class TunnelClientNettyIntegrationTest {

    private NettyGatewayServer gatewayServer;
    private int gatewayPort;
    private TunnelClient tunnelClient;
    private OkHttpClient visitorHttpClient;

    @Before
    public void setUp() throws Exception {
        // 1. Start Netty Gateway on dynamic port
        GatewayConfig config = new GatewayConfig()
                .setPort(0)
                .setBaseDomain("localhost")
                .setRequestTimeoutMs(5000);

        gatewayServer = new NettyGatewayServer(config);
        gatewayServer.start();
        gatewayPort = gatewayServer.getPort();

        visitorHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @After
    public void tearDown() {
        if (tunnelClient != null) {
            tunnelClient.disconnect();
        }
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
    }

    @Test
    public void testAndroidTunnelWithNettyGatewayEndToEnd() throws Exception {
        // 2. Setup mock Android HttpRequestHandler
        HttpRequestHandler requestHandler = new HttpRequestHandler() {
            @NonNull
            @Override
            public HttpResponse handle(@NonNull HttpRequest request) {
                if ("/index.html".equals(request.getPath())) {
                    byte[] htmlBytes = "<!DOCTYPE html><html><body><h1>PocketServer Live!</h1></body></html>"
                            .getBytes(StandardCharsets.UTF_8);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "text/html; charset=UTF-8");
                    headers.put("ETag", "W/\"test-etag-1\"");
                    return new HttpResponse(200, "OK", headers, new ByteArrayInputStream(htmlBytes), (long) htmlBytes.length);
                } else if ("/style.css".equals(request.getPath())) {
                    byte[] cssBytes = "body { background: #111; color: #eee; }".getBytes(StandardCharsets.UTF_8);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "text/css; charset=UTF-8");
                    return new HttpResponse(200, "OK", headers, new ByteArrayInputStream(cssBytes), (long) cssBytes.length);
                }
                return HttpResponse.notFound("Resource not found");
            }
        };

        // 3. Connect Android TunnelClient
        CountDownLatch registeredLatch = new CountDownLatch(1);
        AtomicReference<String> publicUrlRef = new AtomicReference<>();
        AtomicReference<String> assignedHostnameRef = new AtomicReference<>();

        tunnelClient = new TunnelClient(
                "ws://127.0.0.1:" + gatewayPort + "/tunnel",
                "dep-android-100",
                "proj-android-200",
                "tok-sample",
                "mycoolsite",
                requestHandler,
                new TunnelClient.TunnelStateListener() {
                    @Override
                    public void onConnecting() {}

                    @Override
                    public void onRegistered(@NonNull String publicUrl, @NonNull String assignedHostname) {
                        publicUrlRef.set(publicUrl);
                        assignedHostnameRef.set(assignedHostname);
                        registeredLatch.countDown();
                    }

                    @Override
                    public void onDisconnected(int code, @NonNull String reason) {}

                    @Override
                    public void onReconnecting(int attempt, long delayMs) {}

                    @Override
                    public void onError(@NonNull Throwable error) {}
                }
        );

        tunnelClient.connect();

        assertTrue("TunnelClient should register within 5s", registeredLatch.await(5, TimeUnit.SECONDS));
        assertEquals("mycoolsite", assignedHostnameRef.get());
        assertTrue(tunnelClient.isConnected());

        // 4. External visitor requests /index.html via Netty Gateway
        Request indexRequest = new Request.Builder()
                .url("http://127.0.0.1:" + gatewayPort + "/index.html")
                .addHeader("Host", "mycoolsite.localhost")
                .build();

        try (Response indexResponse = visitorHttpClient.newCall(indexRequest).execute()) {
            assertEquals(200, indexResponse.code());
            assertEquals("text/html; charset=UTF-8", indexResponse.header("Content-Type"));
            assertEquals("W/\"test-etag-1\"", indexResponse.header("ETag"));
            assertNotNull(indexResponse.body());
            String body = indexResponse.body().string();
            assertEquals("<!DOCTYPE html><html><body><h1>PocketServer Live!</h1></body></html>", body);
        }

        // 5. External visitor requests /style.css via Netty Gateway
        Request cssRequest = new Request.Builder()
                .url("http://127.0.0.1:" + gatewayPort + "/style.css")
                .addHeader("Host", "mycoolsite.localhost")
                .build();

        try (Response cssResponse = visitorHttpClient.newCall(cssRequest).execute()) {
            assertEquals(200, cssResponse.code());
            assertEquals("text/css; charset=UTF-8", cssResponse.header("Content-Type"));
            assertNotNull(cssResponse.body());
            assertEquals("body { background: #111; color: #eee; }", cssResponse.body().string());
        }

        // 6. External visitor requests non-existent page -> 404
        Request missingRequest = new Request.Builder()
                .url("http://127.0.0.1:" + gatewayPort + "/missing.html")
                .addHeader("Host", "mycoolsite.localhost")
                .build();

        try (Response missingResponse = visitorHttpClient.newCall(missingRequest).execute()) {
            assertEquals(404, missingResponse.code());
        }

        // 7. Disconnect tunnel and verify subsequent visitor request returns 404 offline
        tunnelClient.disconnect();
        // Allow brief moment for TCP unregistration
        Thread.sleep(100);

        try (Response offlineResponse = visitorHttpClient.newCall(indexRequest).execute()) {
            assertEquals(404, offlineResponse.code());
            assertTrue(offlineResponse.body().string().contains("Deployment Offline"));
        }
    }
}
