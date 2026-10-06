package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Protocol-agnostic HTTP response with support for streaming responses,
 * headers, range metadata, and error templates.
 */
public class HttpResponse implements Closeable {

    private final int statusCode;
    private final String statusMessage;
    private final Map<String, String> headers;
    private final InputStream bodyStream;
    private final long contentLength;

    public HttpResponse(int statusCode,
                        @NonNull String statusMessage,
                        @NonNull Map<String, String> headers,
                        @Nullable InputStream bodyStream,
                        long contentLength) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.bodyStream = bodyStream;
        this.contentLength = contentLength;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Nullable
    public String getHeader(@NonNull String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nullable
    public InputStream getBodyStream() {
        return bodyStream;
    }

    public long getContentLength() {
        return contentLength;
    }

    public boolean hasBody() {
        return bodyStream != null && contentLength != 0;
    }

    @Override
    public void close() throws IOException {
        if (bodyStream != null) {
            bodyStream.close();
        }
    }

    // --- Standard HTTP Response Factories ---

    public static HttpResponse ok(@NonNull String mimeType,
                                  @Nullable InputStream stream,
                                  long length,
                                  @Nullable String etag,
                                  @Nullable String cacheControl) {
        Map<String, String> headers = createBaseHeaders(mimeType, length);
        if (etag != null) {
            headers.put("ETag", etag);
        }
        if (cacheControl != null) {
            headers.put("Cache-Control", cacheControl);
        }
        headers.put("Accept-Ranges", "bytes");
        return new HttpResponse(200, "OK", headers, stream, length);
    }

    public static HttpResponse partialContent(@NonNull String mimeType,
                                              @Nullable InputStream stream,
                                              long length,
                                              @NonNull String contentRange,
                                              @Nullable String etag,
                                              @Nullable String cacheControl) {
        Map<String, String> headers = createBaseHeaders(mimeType, length);
        headers.put("Content-Range", contentRange);
        headers.put("Accept-Ranges", "bytes");
        if (etag != null) {
            headers.put("ETag", etag);
        }
        if (cacheControl != null) {
            headers.put("Cache-Control", cacheControl);
        }
        return new HttpResponse(206, "Partial Content", headers, stream, length);
    }

    public static HttpResponse notModified(@NonNull String etag, @Nullable String cacheControl) {
        Map<String, String> headers = new HashMap<>();
        headers.put("ETag", etag);
        if (cacheControl != null) {
            headers.put("Cache-Control", cacheControl);
        }
        headers.put("Access-Control-Allow-Origin", "*");
        return new HttpResponse(304, "Not Modified", headers, null, 0);
    }

    public static HttpResponse badRequest(@NonNull String message) {
        return createTextResponse(400, "Bad Request", message);
    }

    public static HttpResponse forbidden(@NonNull String message) {
        return createTextResponse(403, "Forbidden", message);
    }

    public static HttpResponse notFound(@NonNull String message) {
        return createTextResponse(404, "Not Found", message);
    }

    public static HttpResponse methodNotAllowed() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Allow", "GET, HEAD");
        headers.put("Content-Type", "text/plain; charset=utf-8");
        byte[] bytes = "405 Method Not Allowed".getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Length", String.valueOf(bytes.length));
        return new HttpResponse(405, "Method Not Allowed", headers, new ByteArrayInputStream(bytes), bytes.length);
    }

    public static HttpResponse rangeNotSatisfiable(long totalLength) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Range", "bytes */" + totalLength);
        headers.put("Content-Type", "text/plain; charset=utf-8");
        byte[] bytes = "416 Range Not Satisfiable".getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Length", String.valueOf(bytes.length));
        return new HttpResponse(416, "Range Not Satisfiable", headers, new ByteArrayInputStream(bytes), bytes.length);
    }

    public static HttpResponse internalError(@NonNull String message) {
        return createTextResponse(500, "Internal Server Error", message);
    }

    private static HttpResponse createTextResponse(int code, String statusMsg, String bodyText) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=utf-8");
        headers.put("Access-Control-Allow-Origin", "*");
        byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Length", String.valueOf(bytes.length));
        return new HttpResponse(code, statusMsg, headers, new ByteArrayInputStream(bytes), bytes.length);
    }

    private static Map<String, String> createBaseHeaders(String mimeType, long length) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", mimeType);
        if (length >= 0) {
            headers.put("Content-Length", String.valueOf(length));
        }
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }
}
