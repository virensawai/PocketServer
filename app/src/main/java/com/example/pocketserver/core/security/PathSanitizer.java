package com.example.pocketserver.core.security;

import androidx.annotation.Nullable;
import com.example.pocketserver.core.config.LimitsConfig;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade canonical virtual-path validation pipeline.
 * Defends strictly against directory traversal (../, ..%2F, %2e%2e, double encoding, multi-pass),
 * null-byte injection, drive-letter escapes, control characters, and hidden file access.
 */
public class PathSanitizer {

    /**
     * Sanitizes and normalizes an incoming HTTP request URI path into a safe,
     * canonical relative path within the virtual project root sandbox.
     *
     * @param rawUri the raw requested path (e.g. "/assets/app.js", "/..%2fetc/passwd")
     * @return the normalized relative path (e.g. "assets/app.js", "index.html"),
     *         or {@code null} if the path is invalid, malformed, or attempts traversal.
     */
    @Nullable
    public String sanitize(@Nullable String rawUri) {
        if (rawUri == null || rawUri.isEmpty()) {
            return "index.html";
        }

        // 1. Length validation
        if (rawUri.length() > LimitsConfig.MAX_URL_LENGTH) {
            return null;
        }

        // 2. Strip query string and fragment if present
        String path = rawUri;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        int fragmentIdx = path.indexOf('#');
        if (fragmentIdx >= 0) {
            path = path.substring(0, fragmentIdx);
        }

        // 3. Multi-pass URL decoding to detect double-encoded attacks (e.g. %252e%252e%252f)
        String decoded = path;
        try {
            String previous;
            int decodePasses = 0;
            do {
                previous = decoded;
                decoded = URLDecoder.decode(previous, StandardCharsets.UTF_8.name());
                decodePasses++;
                // If it needs more than 3 passes and continues to mutate, it is a malicious nested encoding attack
                if (decodePasses > 3) {
                    if (!decoded.equals(previous)) {
                        return null; // Malicious nested encoding
                    }
                    break;
                }
            } while (!decoded.equals(previous));
        } catch (Exception e) {
            return null; // Malformed percent encoding
        }

        // 4. Reject null-byte attacks (\0 or %00 or %2500)
        if (decoded.indexOf('\0') >= 0) {
            return null;
        }

        // 5. Reject control characters (ASCII < 32 or == 127) and Unicode replacement character (UTF-8 overlong/decode error)
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c < 32 || c == 127 || c == '\uFFFD') {
                return null;
            }
        }

        // 6. Normalize separators: convert all backslashes to forward slashes
        decoded = decoded.replace('\\', '/');

        // 7. Reject drive letters or absolute OS paths (e.g., "C:", "D:/", "/etc") or protocol schemes
        if (decoded.contains(":") || isRestrictedSystemPath(decoded)) {
            return null;
        }

        // 8. Tokenize path into segments and canonicalize with a stack
        String[] rawSegments = decoded.split("/");
        List<String> canonicalSegments = new ArrayList<>();

        for (String segment : rawSegments) {
            String trimmed = segment.trim();

            if (trimmed.isEmpty() || trimmed.equals(".")) {
                // Ignore empty segments (consecutive slashes) or current-dir "."
                continue;
            }

            if (trimmed.equals("..")) {
                // Attempt to step up
                if (canonicalSegments.isEmpty()) {
                    // Attempt to escape above virtual root! Reject immediately!
                    return null;
                }
                canonicalSegments.remove(canonicalSegments.size() - 1);
            } else {
                // Additional safety: reject any segment containing embedded traversal characters or dots abuse
                if (trimmed.contains("..") || trimmed.contains("\\") || trimmed.startsWith("...")) {
                    return null;
                }

                // Block sensitive system and VCS metadata files
                if (isRestrictedFile(trimmed)) {
                    return null;
                }

                canonicalSegments.add(trimmed);
            }
        }

        // 9. If empty, the request targeted root ("/")
        if (canonicalSegments.isEmpty()) {
            return "index.html";
        }

        // 10. Construct canonical relative path
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < canonicalSegments.size(); i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(canonicalSegments.get(i));
        }

        return builder.toString();
    }

    private static boolean isRestrictedFile(String segment) {
        String lower = segment.toLowerCase();
        return lower.equals(".env")
                || lower.startsWith(".env.")
                || lower.equals(".git")
                || lower.equals(".gitignore")
                || lower.equals(".svn")
                || lower.equals(".ds_store")
                || lower.equals("web-inf")
                || lower.equals("meta-inf");
    }

    private static boolean isRestrictedSystemPath(String decoded) {
        String lower = decoded.toLowerCase();
        return lower.startsWith("/etc/") || lower.equals("/etc")
                || lower.startsWith("/proc/") || lower.equals("/proc")
                || lower.startsWith("/sys/") || lower.equals("/sys")
                || lower.startsWith("/system/") || lower.equals("/system")
                || lower.startsWith("/data/data") || lower.startsWith("/data/system")
                || lower.startsWith("/dev/") || lower.equals("/dev")
                || lower.startsWith("/windows/") || lower.startsWith("/winnt/");
    }
}
