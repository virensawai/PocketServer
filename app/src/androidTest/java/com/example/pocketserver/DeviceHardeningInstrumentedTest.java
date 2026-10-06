package com.example.pocketserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.StaticContentService;
import com.example.pocketserver.core.server.adapter.NanoHttpdServerAdapter;
import com.example.pocketserver.core.storage.ProjectFileResolver;
import com.example.pocketserver.core.storage.SafResource;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.service.PowerLockManager;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Phase 7: Real-Device Hardening & Concurrency Test Suite.
 * Runs on physical Android devices to verify:
 * 1. Security Penetration & Directory Traversal immunity.
 * 2. 10 Concurrent visitor connections streaming large assets without memory exhaustion.
 * 3. RFC 7233 Byte-Range streaming & RFC 7232 ETag caching.
 * 4. SPA Fallback routing.
 * 5. OEM PowerLockManager CPU wake-lock lifecycle.
 */
@RunWith(AndroidJUnit4.class)
public class DeviceHardeningInstrumentedTest {

    private static final int TEST_PORT = 8089;
    private static final int LARGE_ASSET_SIZE = 512 * 1024; // 512 KB

    private Context context;
    private InMemoryProjectResolver resolver;
    private StaticContentService staticService;
    private NanoHttpdServerAdapter serverAdapter;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        resolver = new InMemoryProjectResolver();

        // 1. Populate virtual static website files
        byte[] indexHtmlBytes = "<!DOCTYPE html><html><body><h1>PocketServer Live</h1></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        resolver.addFile("index.html", indexHtmlBytes, "text/html; charset=utf-8");

        byte[] cssBytes = "body { background-color: #0d1117; color: #fff; }".getBytes(StandardCharsets.UTF_8);
        resolver.addFile("assets/style.css", cssBytes, "text/css; charset=utf-8");

        // 2. Large asset for concurrency testing (512 KB filled with patterned bytes)
        byte[] largeAssetBytes = new byte[LARGE_ASSET_SIZE];
        Arrays.fill(largeAssetBytes, (byte) 0x7E);
        resolver.addFile("assets/large_asset.bin", largeAssetBytes, "application/octet-stream");

        // 3. Start real local HTTP server on device port 8089
        staticService = new StaticContentService(resolver);
        staticService.setSpaFallbackEnabled(true);

        serverAdapter = new NanoHttpdServerAdapter("127.0.0.1", TEST_PORT, staticService);
        serverAdapter.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @After
    public void tearDown() {
        if (serverAdapter != null) {
            serverAdapter.stop();
            serverAdapter = null;
        }
    }

    @Test
    public void testStandardGetServesWebsiteOnDevice() throws Exception {
        HttpResponseData response = executeGet("http://127.0.0.1:" + TEST_PORT + "/");
        assertEquals(200, response.statusCode);
        assertEquals("text/html; charset=utf-8", response.contentType);
        assertTrue(new String(response.body, StandardCharsets.UTF_8).contains("PocketServer Live"));
        assertNotNull(response.etag);
    }

    @Test
    public void testDirectoryTraversalAttacksBlockedOnDevice() throws Exception {
        // Path Traversal Variants tested directly over raw TCP socket
        String[] attackUris = new String[] {
                "/../etc/passwd",
                "/..%2Fetc/passwd",
                "/%2e%2e/data/data",
                "/%252e%252e%252fetc/passwd",
                "/%25252e%25252e%25252fescape",
                "/sub/../../escape.txt",
                "/assets/..%5c..%5csecret.txt",
                "/index.html%00.png",
                "/.../secret",
                "/.env",
                "/.env.production",
                "/.git/config",
                "/.gitignore",
                "/.DS_Store",
                "/WEB-INF/web.xml",
                "/etc/passwd"
        };

        for (String uri : attackUris) {
            String rawRequest = "GET " + uri + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
            RawResponse response = executeRawHttp("127.0.0.1", TEST_PORT, rawRequest);
            assertEquals("Attack URI should be blocked with 403 Forbidden: " + uri, 403, response.statusCode);
        }
    }

    @Test
    public void testConcurrencyTenSimultaneousVisitorsLargeAssets() throws Exception {
        int visitorCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(visitorCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(visitorCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger totalBytesTransferred = new AtomicInteger(0);

        String assetUrl = "http://127.0.0.1:" + TEST_PORT + "/assets/large_asset.bin";

        for (int i = 0; i < visitorCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await(); // Synchronize all 10 threads to hit server at exact same instant
                    HttpResponseData response = executeGet(assetUrl);
                    if (response.statusCode == 200 && response.body.length == LARGE_ASSET_SIZE) {
                        successCounter.incrementAndGet();
                        totalBytesTransferred.addAndGet(response.body.length);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 10 requests simultaneously
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);

        executor.shutdown();
        assertTrue("All 10 simultaneous visitor connections must complete in time", finished);
        assertEquals("All 10 visitor requests must succeed with HTTP 200", visitorCount, successCounter.get());
        assertEquals("All 10 visitor requests must transfer the full 512 KB asset",
                visitorCount * LARGE_ASSET_SIZE, totalBytesTransferred.get());
    }

    @Test
    public void testRfc7233ByteRangeAndCachingOnDevice() throws Exception {
        // Range request for first 100 bytes
        URL url = new URL("http://127.0.0.1:" + TEST_PORT + "/assets/large_asset.bin");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Range", "bytes=0-99");
        conn.connect();

        int status = conn.getResponseCode();
        assertEquals(206, status);
        assertEquals("bytes 0-99/" + LARGE_ASSET_SIZE, conn.getHeaderField("Content-Range"));
        assertEquals("100", conn.getHeaderField("Content-Length"));

        byte[] partial = readAllBytes(conn.getInputStream());
        assertEquals(100, partial.length);
        conn.disconnect();

        // ETag conditional request
        HttpResponseData initialRes = executeGet(url.toString());
        assertNotNull(initialRes.etag);

        String conditionalReq = "GET /assets/large_asset.bin HTTP/1.1\r\nHost: 127.0.0.1\r\nIf-None-Match: "
                + initialRes.etag + "\r\nConnection: close\r\n\r\n";
        RawResponse cachedRes = executeRawHttp("127.0.0.1", TEST_PORT, conditionalReq);
        assertEquals(304, cachedRes.statusCode);
    }

    @Test
    public void testSpaRoutingFallbackOnDevice() throws Exception {
        // Deep SPA route: should fallback to index.html with 200 OK
        HttpResponseData spaRes = executeGet("http://127.0.0.1:" + TEST_PORT + "/dashboard/settings/profile");
        assertEquals(200, spaRes.statusCode);
        assertTrue(new String(spaRes.body, StandardCharsets.UTF_8).contains("PocketServer Live"));

        // Missing asset: should NOT fallback, should return 404
        HttpResponseData assetRes = executeGet("http://127.0.0.1:" + TEST_PORT + "/assets/missing.js");
        assertEquals(404, assetRes.statusCode);
    }

    @Test
    public void testPowerLockManagerCpuWakeLockLifecycle() {
        PowerLockManager powerLockManager = new PowerLockManager(context);
        assertFalse(powerLockManager.isHoldingWakeLock());

        // When keepAwake is false, even LIVE should not hold wake lock
        powerLockManager.setKeepAwakeEnabled(false);
        powerLockManager.onDeploymentStateChanged(DeploymentState.LIVE);
        assertFalse(powerLockManager.isHoldingWakeLock());

        // When keepAwake is enabled AND state is LIVE, wake lock is acquired
        powerLockManager.setKeepAwakeEnabled(true);
        assertTrue(powerLockManager.isHoldingWakeLock());

        // When state changes to NETWORK_LOST, wake lock is released
        powerLockManager.onDeploymentStateChanged(DeploymentState.NETWORK_LOST);
        assertFalse(powerLockManager.isHoldingWakeLock());

        // Back to LIVE -> reacquired
        powerLockManager.onDeploymentStateChanged(DeploymentState.LIVE);
        assertTrue(powerLockManager.isHoldingWakeLock());

        // STOPPED -> released
        powerLockManager.onDeploymentStateChanged(DeploymentState.STOPPED);
        assertFalse(powerLockManager.isHoldingWakeLock());

        powerLockManager.releaseAll();
        assertFalse(powerLockManager.isHoldingWakeLock());
    }

    @Test
    public void testHostingRealHtmlFileFromDeviceStorage() throws Exception {
        // 1. Create a physical directory on Android device storage
        java.io.File siteDir = new java.io.File(context.getFilesDir(), "real_sample_site");
        if (!siteDir.exists()) {
            siteDir.mkdirs();
        }

        // 2. Write physical index.html file
        java.io.File indexFile = new java.io.File(siteDir, "index.html");
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><title>PocketServer Physical Test</title></head>\n" +
                "<body><h1>Hosted from physical device filesystem!</h1><p id=\"test-p\">Success</p></body>\n" +
                "</html>";
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(indexFile)) {
            fos.write(htmlContent.getBytes(StandardCharsets.UTF_8));
        }

        // 3. Write physical CSS file
        java.io.File cssFile = new java.io.File(siteDir, "style.css");
        String cssContent = "body { background-color: #121212; color: #00ff00; }";
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(cssFile)) {
            fos.write(cssContent.getBytes(StandardCharsets.UTF_8));
        }

        // 4. Validate using ProjectValidator
        android.net.Uri siteUri = android.net.Uri.fromFile(siteDir);
        com.example.pocketserver.core.storage.ProjectValidator.ValidationResult val =
                com.example.pocketserver.core.storage.ProjectValidator.validate(context, siteUri);
        assertTrue("Project folder should be valid", val.isValid());
        assertTrue("Project folder must detect index.html", val.hasIndexHtml());
        assertTrue("Project folder must have at least 2 files", val.getImmediateItemCount() >= 2);

        // 5. Create real HybridSafFileResolver pointing to physical folder
        com.example.pocketserver.core.storage.HybridSafFileResolver realResolver =
                new com.example.pocketserver.core.storage.HybridSafFileResolver(context, siteUri);

        // 6. Start real NanoHttpd server adapter with real HybridSafFileResolver on a separate port
        int realPort = 8092;
        StaticContentService realStaticService = new StaticContentService(realResolver);
        NanoHttpdServerAdapter realServer = new NanoHttpdServerAdapter("127.0.0.1", realPort, realStaticService);
        try {
            realServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            // 7. Request root GET /
            HttpResponseData rootRes = executeGet("http://127.0.0.1:" + realPort + "/");
            assertEquals(200, rootRes.statusCode);
            assertTrue("Expected text/html, got " + rootRes.contentType, rootRes.contentType.contains("text/html"));
            String rootBody = new String(rootRes.body, StandardCharsets.UTF_8);
            assertTrue("Should contain heading from physical index.html",
                    rootBody.contains("Hosted from physical device filesystem!"));

            // 8. Request direct GET /index.html
            HttpResponseData indexRes = executeGet("http://127.0.0.1:" + realPort + "/index.html");
            assertEquals(200, indexRes.statusCode);
            assertTrue(new String(indexRes.body, StandardCharsets.UTF_8).contains("Success"));

            // 9. Request physical GET /style.css
            HttpResponseData cssRes = executeGet("http://127.0.0.1:" + realPort + "/style.css");
            assertEquals(200, cssRes.statusCode);
            assertTrue("Expected text/css, got " + cssRes.contentType, cssRes.contentType.contains("text/css"));
            assertTrue(new String(cssRes.body, StandardCharsets.UTF_8).contains("#00ff00"));

        } finally {
            realServer.stop();
            // Cleanup files
            indexFile.delete();
            cssFile.delete();
            siteDir.delete();
        }
    }

    // --- Helper Methods ---

    private static HttpResponseData executeGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();

        int statusCode = conn.getResponseCode();
        String contentType = conn.getHeaderField("Content-Type");
        String etag = conn.getHeaderField("ETag");

        InputStream in = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
        byte[] body = in != null ? readAllBytes(in) : new byte[0];
        conn.disconnect();

        return new HttpResponseData(statusCode, contentType, etag, body);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static class HttpResponseData {
        final int statusCode;
        final String contentType;
        final String etag;
        final byte[] body;

        HttpResponseData(int statusCode, String contentType, String etag, byte[] body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.etag = etag;
            this.body = body;
        }
    }

    private static class InMemoryProjectResolver implements ProjectFileResolver {
        private final Map<String, byte[]> files = new HashMap<>();
        private final Map<String, String> mimes = new HashMap<>();
        private final Map<String, Long> lastModifiedMap = new HashMap<>();

        public void addFile(String path, byte[] content, String mime) {
            files.put(path, content);
            mimes.put(path, mime);
            lastModifiedMap.put(path, 1700000000000L);
        }

        @Nullable
        @Override
        public SafResource resolve(@NonNull String sanitizedPath) {
            byte[] data = files.get(sanitizedPath);
            if (data == null) {
                return null;
            }
            Long lastModified = lastModifiedMap.get(sanitizedPath);
            return new SafResource(
                    null,
                    sanitizedPath,
                    sanitizedPath,
                    data.length,
                    lastModified != null ? lastModified : 1700000000000L,
                    mimes.get(sanitizedPath),
                    false,
                    true
            );
        }

        @NonNull
        @Override
        public InputStream openInputStream(@NonNull SafResource resource) {
            byte[] data = files.get(resource.getVirtualRelativePath());
            if (data == null) {
                return new ByteArrayInputStream(new byte[0]);
            }
            return new ByteArrayInputStream(data);
        }

        @Nullable
        @Override
        public AssetFileDescriptor openAssetFileDescriptor(@NonNull SafResource resource) {
            return null;
        }
    }

    private static RawResponse executeRawHttp(String host, int port, String requestString) throws IOException {
        try (java.net.Socket socket = new java.net.Socket(host, port)) {
            socket.setSoTimeout(5000);
            java.io.OutputStream out = socket.getOutputStream();
            out.write(requestString.getBytes(StandardCharsets.UTF_8));
            out.flush();

            java.io.InputStream in = socket.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            int statusCode = 0;
            if (statusLine != null && statusLine.startsWith("HTTP/")) {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    try {
                        statusCode = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            String line;
            Map<String, String> headers = new HashMap<>();
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }

            return new RawResponse(statusCode, headers);
        }
    }

    private static class RawResponse {
        final int statusCode;
        final Map<String, String> headers;

        RawResponse(int statusCode, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.headers = headers;
        }
    }
}
