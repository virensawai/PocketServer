package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MIME type resolver supporting modern web standards (WASM, WebP, WOFF2, video, audio)
 * and static asset detection for SPA fallback exclusion.
 */
public final class MimeTypes {

    private static final Map<String, String> MIME_MAP;
    private static final Set<String> STATIC_ASSET_EXTENSIONS;

    static {
        Map<String, String> map = new HashMap<>();
        // Documents & Text
        map.put("html", "text/html; charset=utf-8");
        map.put("htm", "text/html; charset=utf-8");
        map.put("css", "text/css; charset=utf-8");
        map.put("js", "application/javascript; charset=utf-8");
        map.put("mjs", "application/javascript; charset=utf-8");
        map.put("json", "application/json; charset=utf-8");
        map.put("xml", "application/xml; charset=utf-8");
        map.put("txt", "text/plain; charset=utf-8");
        map.put("md", "text/markdown; charset=utf-8");
        map.put("map", "application/json; charset=utf-8");

        // Images
        map.put("png", "image/png");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("gif", "image/gif");
        map.put("svg", "image/svg+xml");
        map.put("webp", "image/webp");
        map.put("avif", "image/avif");
        map.put("ico", "image/x-icon");
        map.put("bmp", "image/bmp");

        // Fonts
        map.put("woff", "font/woff");
        map.put("woff2", "font/woff2");
        map.put("ttf", "font/ttf");
        map.put("otf", "font/otf");
        map.put("eot", "application/vnd.ms-fontobject");

        // Media
        map.put("mp4", "video/mp4");
        map.put("webm", "video/webm");
        map.put("ogv", "video/ogg");
        map.put("mp3", "audio/mpeg");
        map.put("wav", "audio/wav");
        map.put("ogg", "audio/ogg");

        // WebAssembly & Binary
        map.put("wasm", "application/wasm");
        map.put("pdf", "application/pdf");
        map.put("zip", "application/zip");
        map.put("gz", "application/gzip");

        MIME_MAP = Collections.unmodifiableMap(map);

        // Extensions that denote static assets which should NEVER receive SPA fallback
        Set<String> assets = new HashSet<>();
        assets.add("js");
        assets.add("mjs");
        assets.add("css");
        assets.add("png");
        assets.add("jpg");
        assets.add("jpeg");
        assets.add("gif");
        assets.add("webp");
        assets.add("avif");
        assets.add("svg");
        assets.add("ico");
        assets.add("woff");
        assets.add("woff2");
        assets.add("ttf");
        assets.add("otf");
        assets.add("json");
        assets.add("wasm");
        assets.add("mp4");
        assets.add("webm");
        assets.add("mp3");
        assets.add("wav");
        assets.add("xml");
        assets.add("map");
        assets.add("pdf");
        assets.add("zip");
        STATIC_ASSET_EXTENSIONS = Collections.unmodifiableSet(assets);
    }

    private MimeTypes() {
        // Utility
    }

    /**
     * Look up the MIME type for a given file name or path.
     *
     * @param path file name or path (e.g. "bundle.js", "images/logo.png")
     * @return matching MIME type, or "application/octet-stream" if unrecognized.
     */
    @NonNull
    public static String getMimeType(@NonNull String path) {
        String ext = getExtension(path);
        if (ext == null) {
            return "application/octet-stream";
        }
        String mime = MIME_MAP.get(ext.toLowerCase(Locale.ROOT));
        return (mime != null) ? mime : "application/octet-stream";
    }

    /**
     * Determines whether the given path represents a static asset
     * (e.g. .js, .css, .png, .wasm) that should bypass SPA fallback.
     *
     * @param path requested path
     * @return true if file has a known static asset extension.
     */
    public static boolean isStaticAsset(@NonNull String path) {
        String ext = getExtension(path);
        if (ext == null) {
            return false;
        }
        return STATIC_ASSET_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
    }

    /**
     * Extracts lowercase extension from file path without the leading dot.
     */
    @NonNull
    public static String getExtension(@NonNull String path) {
        int dotIdx = path.lastIndexOf('.');
        int slashIdx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (dotIdx > slashIdx && dotIdx < path.length() - 1) {
            return path.substring(dotIdx + 1);
        }
        return "";
    }
}
