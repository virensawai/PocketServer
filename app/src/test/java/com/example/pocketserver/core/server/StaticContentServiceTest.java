package com.example.pocketserver.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.storage.ProjectFileResolver;
import com.example.pocketserver.core.storage.SafResource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class StaticContentServiceTest {

    private InMemoryResolver resolver;
    private StaticContentService service;

    @Before
    public void setUp() {
        resolver = new InMemoryResolver();
        resolver.addFile("index.html", "<!DOCTYPE html><html><body>Home</body></html>", "text/html; charset=utf-8", 1000L);
        resolver.addFile("assets/style.css", "body { color: red; }", "text/css; charset=utf-8", 2000L);
        resolver.addFile("video.mp4", "0123456789ABCDEF", "video/mp4", 3000L);

        service = new StaticContentService(resolver);
    }

    @Test
    public void testGetRootServesIndexHtml() throws Exception {
        HttpRequest request = new HttpRequest("GET", "/", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeader("Content-Type"));
        assertNotNull(response.getHeader("ETag"));
        assertNotNull(response.getBodyStream());

        String body = readStream(response.getBodyStream());
        assertTrue(body.contains("Home"));
    }

    @Test
    public void testHeadMethodReturnsHeadersWithoutBody() {
        HttpRequest request = new HttpRequest("HEAD", "/index.html", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeader("Content-Type"));
        assertNotNull(response.getHeader("ETag"));
        assertNull(response.getBodyStream());
        assertFalse(response.hasBody());
    }

    @Test
    public void testMethodNotAllowedForPost() {
        HttpRequest request = new HttpRequest("POST", "/index.html", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(405, response.getStatusCode());
    }

    @Test
    public void testDirectoryTraversalBlockedWith403() {
        HttpRequest request = new HttpRequest("GET", "/../../etc/passwd", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(403, response.getStatusCode());
    }

    @Test
    public void testSpaFallbackServesIndexHtmlForVirtualRoutes() throws Exception {
        service.setSpaFallbackEnabled(true);

        HttpRequest request = new HttpRequest("GET", "/users/profile/123", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(200, response.getStatusCode());
        String body = readStream(response.getBodyStream());
        assertTrue(body.contains("Home"));
    }

    @Test
    public void testSpaFallbackDoesNotApplyToMissingStaticAssets() {
        service.setSpaFallbackEnabled(true);

        HttpRequest request = new HttpRequest("GET", "/assets/missing-bundle.js", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(404, response.getStatusCode());
    }

    @Test
    public void testSpaFallbackDisabledReturns404() {
        service.setSpaFallbackEnabled(false);

        HttpRequest request = new HttpRequest("GET", "/dashboard", null, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(404, response.getStatusCode());
    }

    @Test
    public void testETagConditionalRequestReturns304NotModified() {
        HttpRequest initialReq = new HttpRequest("GET", "/index.html", null, null, null);
        HttpResponse initialRes = service.handle(initialReq);
        String etag = initialRes.getHeader("ETag");
        assertNotNull(etag);

        Map<String, String> headers = new HashMap<>();
        headers.put("if-none-match", etag);
        HttpRequest cachedReq = new HttpRequest("GET", "/index.html", headers, null, null);
        HttpResponse cachedRes = service.handle(cachedReq);

        assertEquals(304, cachedRes.getStatusCode());
        assertNull(cachedRes.getBodyStream());
    }

    @Test
    public void testRangeRequestReturns206PartialContent() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("range", "bytes=0-4"); // First 5 bytes of "0123456789ABCDEF"
        HttpRequest request = new HttpRequest("GET", "/video.mp4", headers, null, null);
        HttpResponse response = service.handle(request);

        assertEquals(206, response.getStatusCode());
        assertEquals("bytes 0-4/16", response.getHeader("Content-Range"));
        assertEquals("5", response.getHeader("Content-Length"));

        String partialBody = readStream(response.getBodyStream());
        assertEquals("01234", partialBody);
    }

    private static String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static class InMemoryResolver implements ProjectFileResolver {
        private final Map<String, byte[]> fileContents = new HashMap<>();
        private final Map<String, String> mimeTypes = new HashMap<>();
        private final Map<String, Long> lastModifiedTimes = new HashMap<>();

        public void addFile(String path, String content, String mime, long lastModified) {
            fileContents.put(path, content.getBytes(StandardCharsets.UTF_8));
            mimeTypes.put(path, mime);
            lastModifiedTimes.put(path, lastModified);
        }

        @Nullable
        @Override
        public SafResource resolve(@NonNull String sanitizedPath) {
            byte[] data = fileContents.get(sanitizedPath);
            if (data == null) {
                return null;
            }
            return new SafResource(
                    null,
                    sanitizedPath,
                    sanitizedPath,
                    data.length,
                    lastModifiedTimes.get(sanitizedPath),
                    mimeTypes.get(sanitizedPath),
                    false,
                    true
            );
        }

        @NonNull
        @Override
        public InputStream openInputStream(@NonNull SafResource resource) {
            byte[] data = fileContents.get(resource.getVirtualRelativePath());
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
}
