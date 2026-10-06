package com.example.pocketserver.engine.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Unit tests for TelemetryTrackingRequestHandler ensuring request counting,
 * stream concurrency, and byte counting through counting streams.
 */
public class TelemetryTrackingRequestHandlerTest {

    @Test
    public void testTrackingWithBodyStream() throws IOException {
        byte[] payload = "Hello PocketServer Stream!".getBytes(StandardCharsets.UTF_8);
        HttpRequestHandler mockDelegate = request -> HttpResponse.ok(
                "text/plain",
                new ByteArrayInputStream(payload),
                payload.length,
                null,
                null
        );

        DataUsageTracker dataUsageTracker = new DataUsageTracker();
        AtomicReference<DeploymentTelemetry> lastTelemetry = new AtomicReference<>();

        TelemetryTrackingRequestHandler handler = new TelemetryTrackingRequestHandler(
                mockDelegate,
                dataUsageTracker,
                lastTelemetry::set
        );

        HttpRequest request = new HttpRequest("GET", "/test.txt", Collections.emptyMap(), "", "127.0.0.1");
        HttpResponse response = handler.handle(request);

        assertNotNull(response);
        assertEquals(1, handler.getSnapshot().getRequestCount());
        assertEquals(1, handler.getSnapshot().getActiveStreams());

        // Read all bytes from the body stream
        InputStream bodyStream = response.getBodyStream();
        assertNotNull(bodyStream);
        byte[] buffer = new byte[128];
        int bytesRead = bodyStream.read(buffer);
        assertEquals(payload.length, bytesRead);

        // Close stream
        bodyStream.close();

        // Active streams should now be 0
        assertEquals(0, handler.getSnapshot().getActiveStreams());
        assertEquals(payload.length, handler.getSnapshot().getBytesTransferred());
        assertEquals(payload.length, dataUsageTracker.getBytesTransferred());
    }

    @Test
    public void testTrackingWithNoBodyStream() {
        HttpRequestHandler mockDelegate = request -> HttpResponse.notModified("\"etag-123\"", null);

        DataUsageTracker dataUsageTracker = new DataUsageTracker();
        TelemetryTrackingRequestHandler handler = new TelemetryTrackingRequestHandler(
                mockDelegate,
                dataUsageTracker,
                null
        );

        HttpRequest request = new HttpRequest("GET", "/index.html", Collections.emptyMap(), "", "127.0.0.1");
        HttpResponse response = handler.handle(request);

        assertNotNull(response);
        assertEquals(304, response.getStatusCode());
        assertEquals(1, handler.getSnapshot().getRequestCount());
        // For responses with no body, active stream should be decremented immediately back to 0
        assertEquals(0, handler.getSnapshot().getActiveStreams());
        assertEquals(0, handler.getSnapshot().getBytesTransferred());
    }

    @Test
    public void testRequestLoggedCallback() {
        byte[] bytes = "Hello World".getBytes(StandardCharsets.UTF_8);
        HttpRequestHandler mockDelegate = request -> HttpResponse.ok(
                "text/plain", new ByteArrayInputStream(bytes), bytes.length, null, null);
        DataUsageTracker dataUsageTracker = new DataUsageTracker();
        AtomicReference<com.example.pocketserver.engine.model.RequestLogItem> loggedItem = new AtomicReference<>();

        TelemetryTrackingRequestHandler handler = new TelemetryTrackingRequestHandler(
                mockDelegate,
                dataUsageTracker,
                new TelemetryTrackingRequestHandler.TelemetryListener() {
                    @Override
                    public void onTelemetryUpdated(DeploymentTelemetry telemetry) {}

                    @Override
                    public void onRequestLogged(com.example.pocketserver.engine.model.RequestLogItem logItem) {
                        loggedItem.set(logItem);
                    }
                }
        );

        HttpRequest request = new HttpRequest("GET", "/api/ping", Collections.emptyMap(), "", "127.0.0.1");
        HttpResponse response = handler.handle(request);

        assertNotNull(response);
        // Reading body triggers completion callback
        if (response.getBodyStream() != null) {
            try {
                byte[] buf = new byte[64];
                while (response.getBodyStream().read(buf) != -1) {}
                response.getBodyStream().close();
            } catch (IOException ignored) {}
        }

        assertNotNull(loggedItem.get());
        assertEquals("GET", loggedItem.get().getMethod());
        assertEquals("/api/ping", loggedItem.get().getPath());
        assertEquals(200, loggedItem.get().getStatusCode());
    }
}
