package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import com.example.pocketserver.core.security.PathSanitizer;
import com.example.pocketserver.core.storage.ProjectFileResolver;
import com.example.pocketserver.core.storage.SafResource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Core static website content delivery engine.
 * Implements GET/HEAD processing, RFC 7233 byte-range streaming,
 * RFC 7232 conditional ETag caching, SPA fallback, and security sanitization.
 */
public class StaticContentService implements HttpRequestHandler {

    private final ProjectFileResolver fileResolver;
    private final PathSanitizer pathSanitizer;
    private volatile boolean spaFallbackEnabled = true;

    public StaticContentService(@NonNull ProjectFileResolver fileResolver) {
        this.fileResolver = fileResolver;
        this.pathSanitizer = new PathSanitizer();
    }

    public void setSpaFallbackEnabled(boolean enabled) {
        this.spaFallbackEnabled = enabled;
    }

    public boolean isSpaFallbackEnabled() {
        return spaFallbackEnabled;
    }

    @NonNull
    @Override
    public HttpResponse handle(@NonNull HttpRequest request) {
        String method = request.getMethod();
        boolean isHead = "HEAD".equalsIgnoreCase(method);
        boolean isGet = "GET".equalsIgnoreCase(method);

        // 1. Method verification: only GET and HEAD supported for static hosting
        if (!isGet && !isHead) {
            return HttpResponse.methodNotAllowed();
        }

        // 2. Canonical virtual path sanitization
        String sanitizedPath = pathSanitizer.sanitize(request.getPath());
        if (sanitizedPath == null) {
            return HttpResponse.forbidden("403 Forbidden: Invalid or restricted path.");
        }

        // 3. File resolution
        SafResource resource = fileResolver.resolve(sanitizedPath);

        // 4. SPA Fallback check
        if (resource == null || !resource.exists()) {
            boolean isAsset = MimeTypes.isStaticAsset(sanitizedPath);
            if (spaFallbackEnabled && !isAsset) {
                // Route does not exist, but SPA fallback is ON and it's not a missing asset -> fallback to index.html
                resource = fileResolver.resolve("index.html");
                if (resource == null || !resource.exists()) {
                    return HttpResponse.notFound("404 Not Found: index.html not found.");
                }
            } else {
                return HttpResponse.notFound("404 Not Found: " + sanitizedPath);
            }
        }

        // 5. Caching evaluation (ETag and If-Modified-Since)
        String etag = ETagGenerator.generateETag(
                resource.getVirtualRelativePath(),
                resource.getSize(),
                resource.getLastModified()
        );
        String cacheControl = ETagGenerator.getCacheControlHeader(resource.getVirtualRelativePath());

        String ifNoneMatch = request.getHeader("if-none-match");
        if (ETagGenerator.matchesIfNoneMatch(ifNoneMatch, etag)) {
            return HttpResponse.notModified(etag, cacheControl);
        }

        String ifModifiedSince = request.getHeader("if-modified-since");
        if (ETagGenerator.isNotModifiedSince(ifModifiedSince, resource.getLastModified())) {
            return HttpResponse.notModified(etag, cacheControl);
        }

        // 6. Range request handling
        String rangeHeader = request.getHeader("range");
        if (rangeHeader != null && isGet) {
            try {
                RangeParser.ByteRange byteRange = RangeParser.parse(rangeHeader, resource.getSize());
                if (byteRange != null) {
                    InputStream rawStream = fileResolver.openInputStream(resource);
                    skipFully(rawStream, byteRange.getStart());
                    InputStream boundedStream = new BoundedInputStream(rawStream, byteRange.getLength());

                    return HttpResponse.partialContent(
                            resource.getMimeType(),
                            boundedStream,
                            byteRange.getLength(),
                            byteRange.toContentRangeHeader(),
                            etag,
                            cacheControl
                    );
                }
            } catch (RangeParser.RangeNotSatisfiableException e) {
                return HttpResponse.rangeNotSatisfiable(resource.getSize());
            } catch (IOException e) {
                return HttpResponse.internalError("Error reading file range: " + e.getMessage());
            }
        }

        // 7. Regular full file delivery
        try {
            InputStream stream = isHead ? null : fileResolver.openInputStream(resource);
            return HttpResponse.ok(
                    resource.getMimeType(),
                    stream,
                    resource.getSize(),
                    etag,
                    cacheControl
            );
        } catch (IOException e) {
            return HttpResponse.internalError("Error opening resource stream: " + e.getMessage());
        }
    }

    private static void skipFully(InputStream in, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                // If skip() returned 0, read a single byte to progress
                if (in.read() == -1) {
                    break;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    /**
     * Bounded input stream for slicing Range responses without reading beyond limit.
     */
    private static class BoundedInputStream extends FilterInputStream {
        private long remaining;

        public BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b != -1) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int bytesRead = super.read(b, off, toRead);
            if (bytesRead > 0) {
                remaining -= bytesRead;
            }
            return bytesRead;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), remaining);
        }
    }
}
