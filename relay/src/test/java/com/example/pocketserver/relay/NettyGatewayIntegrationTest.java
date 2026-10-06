package com.example.pocketserver.relay;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.protocol.GatewayControlFrame;
import com.example.pocketserver.relay.protocol.GatewayFrameType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NettyGatewayIntegrationTest {

    private NettyGatewayServer gatewayServer;
    private int port;
    private OkHttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        GatewayConfig config = new GatewayConfig()
                .setPort(0) // dynamic available port
                .setBaseDomain("localhost")
                .setRequestTimeoutMs(5000);

        gatewayServer = new NettyGatewayServer(config);
        gatewayServer.start();
        port = gatewayServer.getPort();

        httpClient = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @After
    public void tearDown() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
    }

    @Test
    public void testFullVisitorRequestToAndroidStreamEndToEnd() throws Exception {
        CountDownLatch registeredLatch = new CountDownLatch(1);
        CountDownLatch requestStartLatch = new CountDownLatch(1);

        AtomicReference<String> receivedRequestId = new AtomicReference<>();
        AtomicReference<WebSocket> androidWsRef = new AtomicReference<>();

        // 1. Android Client connects outbound reverse tunnel
        Request wsRequest = new Request.Builder()
                .url("ws://localhost:" + port + "/tunnel")
                .addHeader("X-Deployment-Id", "dep-live-1")
                .addHeader("X-Project-Id", "proj-live-1")
                .build();

        WebSocket androidWs = httpClient.newWebSocket(wsRequest, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                androidWsRef.set(webSocket);
                // Send REGISTER control frame
                GatewayControlFrame reg = new GatewayControlFrame();
                reg.setType(GatewayFrameType.REGISTER);
                reg.setDeploymentId("dep-live-1");
                reg.setProjectId("proj-live-1");
                reg.setHostname("mysite");
                webSocket.send(reg.toJson());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                GatewayControlFrame frame = GatewayControlFrame.fromJson(text);
                if (frame == null) return;

                if (frame.getType() == GatewayFrameType.REGISTER_OK) {
                    registeredLatch.countDown();
                } else if (frame.getType() == GatewayFrameType.REQUEST_START) {
                    receivedRequestId.set(frame.getRequestId());
                    requestStartLatch.countDown();
                }
            }
        });

        assertTrue("Android client should register within 5s", registeredLatch.await(5, TimeUnit.SECONDS));

        // 2. In background thread, visitor issues HTTP GET request
        AtomicReference<Response> visitorResponseRef = new AtomicReference<>();
        CountDownLatch visitorCompleteLatch = new CountDownLatch(1);

        new Thread(() -> {
            Request visitorReq = new Request.Builder()
                    .url("http://localhost:" + port + "/index.html")
                    .addHeader("Host", "mysite.localhost")
                    .build();
            try {
                Response resp = httpClient.newCall(visitorReq).execute();
                visitorResponseRef.set(resp);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                visitorCompleteLatch.countDown();
            }
        }).start();

        // 3. Android client receives REQUEST_START
        assertTrue("Android should receive REQUEST_START within 5s", requestStartLatch.await(5, TimeUnit.SECONDS));
        String reqId = receivedRequestId.get();
        assertNotNull(reqId);
        UUID requestUuid = UUID.fromString(reqId);

        // 4. Android client replies with RESPONSE_START
        GatewayControlFrame respStart = new GatewayControlFrame();
        respStart.setType(GatewayFrameType.RESPONSE_START);
        respStart.setRequestId(reqId);
        respStart.setStatusCode(200);
        respStart.setStatusMessage("OK");
        respStart.getHeaders().put("Content-Type", "text/html; charset=UTF-8");
        respStart.getHeaders().put("X-Custom-Server", "PocketServer");
        byte[] bodyBytes = "<h1>Hello From Android Phone!</h1>".getBytes(StandardCharsets.UTF_8);
        respStart.setContentLength((long) bodyBytes.length);

        androidWs.send(respStart.toJson());

        // 5. Android client sends raw BINARY chunk with 16-byte UUID header
        byte[] frameBytes = new byte[16 + bodyBytes.length];
        ByteBuffer buf = ByteBuffer.wrap(frameBytes);
        buf.putLong(requestUuid.getMostSignificantBits());
        buf.putLong(requestUuid.getLeastSignificantBits());
        buf.put(bodyBytes);

        androidWs.send(ByteString.of(frameBytes));

        // 6. Android client sends STREAM_END
        GatewayControlFrame streamEnd = new GatewayControlFrame();
        streamEnd.setType(GatewayFrameType.STREAM_END);
        streamEnd.setRequestId(reqId);
        androidWs.send(streamEnd.toJson());

        // 7. Visitor receives complete HTTP response
        assertTrue("Visitor request should finish within 5s", visitorCompleteLatch.await(5, TimeUnit.SECONDS));
        Response visitorResp = visitorResponseRef.get();
        assertNotNull("Visitor response should not be null", visitorResp);
        assertEquals(200, visitorResp.code());
        assertEquals("text/html; charset=UTF-8", visitorResp.header("Content-Type"));
        assertEquals("PocketServer", visitorResp.header("X-Custom-Server"));

        String bodyString = visitorResp.body().string();
        assertEquals("<h1>Hello From Android Phone!</h1>", bodyString);

        androidWs.close(1000, "Done");
    }

    @Test
    public void testUnknownHostReturns404() throws Exception {
        Request visitorReq = new Request.Builder()
                .url("http://localhost:" + port + "/about")
                .addHeader("Host", "nonexistent.localhost")
                .build();

        try (Response response = httpClient.newCall(visitorReq).execute()) {
            assertEquals(404, response.code());
            assertTrue(response.body().string().contains("Deployment Offline"));
        }
    }

    @Test
    public void testRootStatusEndpoint() throws Exception {
        Request visitorReq = new Request.Builder()
                .url("http://localhost:" + port + "/health")
                .addHeader("Host", "localhost")
                .build();

        try (Response response = httpClient.newCall(visitorReq).execute()) {
            assertEquals(200, response.code());
            String json = response.body().string();
            assertTrue(json.contains("\"status\": \"UP\""));
        }
    }
}
