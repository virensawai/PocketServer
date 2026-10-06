package com.example.pocketserver.relay.handler;

import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import java.util.Locale;

/**
 * Intelligent content-aware compression handler.
 * Automatically compresses compressible text resources (HTML, CSS, JS, JSON, SVG, XML)
 * and bypasses already-compressed media types (JPEG, PNG, WebP, MP4, WASM, GZ) to conserve CPU.
 */
public class GatewayCompressionHandler extends HttpContentCompressor {

    public GatewayCompressionHandler() {
        super(6); // Standard gzip compression level
    }

    public GatewayCompressionHandler(int compressionLevel) {
        super(compressionLevel);
    }

    @Override
    protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
        // If already encoded (e.g. content-encoding: gzip), do not re-compress
        if (headers.headers().contains(HttpHeaderNames.CONTENT_ENCODING)) {
            return null;
        }

        String contentType = headers.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            String lowerType = contentType.toLowerCase(Locale.ROOT);
            if (isBypassMediaType(lowerType)) {
                return null;
            }
        }

        return super.beginEncode(headers, acceptEncoding);
    }

    private boolean isBypassMediaType(String contentType) {
        return contentType.contains("image/jpeg") ||
                contentType.contains("image/jpg") ||
                contentType.contains("image/png") ||
                contentType.contains("image/webp") ||
                contentType.contains("image/gif") ||
                contentType.contains("video/mp4") ||
                contentType.contains("video/webm") ||
                contentType.contains("video/ogg") ||
                contentType.contains("application/wasm") ||
                contentType.contains("application/gzip") ||
                contentType.contains("application/zip") ||
                contentType.contains("application/x-zip-compressed") ||
                contentType.contains("application/octet-stream") ||
                contentType.contains("audio/");
    }
}
