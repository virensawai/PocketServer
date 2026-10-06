package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Deterministic HTTP ETag and caching header generator and evaluator.
 */
public final class ETagGenerator {

    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private ETagGenerator() {
        // Utility
    }

    /**
     * Generates a deterministic weak ETag using the relative path, file size, and last modified timestamp.
     *
     * @param path relative file path
     * @param size size in bytes
     * @param lastModified timestamp in milliseconds
     * @return formatted ETag (e.g. W/"a1b2-1048-18e47")
     */
    @NonNull
    public static String generateETag(@NonNull String path, long size, long lastModified) {
        int pathHash = path.hashCode();
        String hexPath = Integer.toHexString(pathHash);
        String hexSize = Long.toHexString(size);
        String hexTime = Long.toHexString(lastModified);
        return "W/\"" + hexPath + "-" + hexSize + "-" + hexTime + "\"";
    }

    /**
     * Formats a Unix timestamp into an RFC 7231 HTTP-date string.
     */
    @NonNull
    public static String formatHttpDate(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date(timestampMillis));
    }

    /**
     * Checks if the client cached entity matches the current entity via If-None-Match.
     *
     * @param ifNoneMatchHeader value of If-None-Match header from client
     * @param currentETag current ETag of the resource
     * @return true if resource matches (eligible for 304 Not Modified)
     */
    public static boolean matchesIfNoneMatch(@Nullable String ifNoneMatchHeader, @NonNull String currentETag) {
        if (ifNoneMatchHeader == null || ifNoneMatchHeader.trim().isEmpty()) {
            return false;
        }
        String trimmed = ifNoneMatchHeader.trim();
        if (trimmed.equals("*")) {
            return true;
        }

        // Support multiple comma-separated ETags
        String[] clientEtags = trimmed.split(",");
        String cleanCurrent = currentETag.replace("W/", "").trim();

        for (String etag : clientEtags) {
            String cleanCandidate = etag.trim().replace("W/", "").trim();
            if (cleanCandidate.equals(cleanCurrent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the client cached entity is up to date via If-Modified-Since.
     */
    public static boolean isNotModifiedSince(@Nullable String ifModifiedSinceHeader, long lastModifiedMillis) {
        if (ifModifiedSinceHeader == null || ifModifiedSinceHeader.trim().isEmpty()) {
            return false;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = sdf.parse(ifModifiedSinceHeader.trim());
            if (date != null) {
                // If the lastModified timestamp is equal to or older than the header date
                // Note: HTTP dates only have 1-second precision
                long headerSeconds = date.getTime() / 1000L;
                long resourceSeconds = lastModifiedMillis / 1000L;
                return resourceSeconds <= headerSeconds;
            }
        } catch (Exception ignored) {
            // Unparseable header, fall back to re-sending
        }
        return false;
    }

    /**
     * Computes an appropriate Cache-Control header based on whether the asset is fingerprinted or static.
     */
    @NonNull
    public static String getCacheControlHeader(@NonNull String path) {
        if (MimeTypes.isStaticAsset(path)) {
            // Check if path appears fingerprinted / hashed (e.g. app.8f2a1b.js or index-D6zK_tM0.js)
            if (path.matches(".*[-._][a-zA-Z0-9]{8,}\\.(js|css|wasm|png|jpg|svg|woff2)$")) {
                return "public, max-age=31536000, immutable";
            }
            return "public, max-age=86400"; // 1 day for regular assets
        }
        // HTML and dynamic routes must revalidate
        return "public, max-age=0, must-revalidate";
    }
}
