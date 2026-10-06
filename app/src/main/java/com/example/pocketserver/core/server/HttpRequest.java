package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Protocol-agnostic HTTP request model used across local HTTP servers and WebSocket tunnels.
 */
public class HttpRequest {

    private final String requestId;
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String queryString;
    private final String clientIp;

    public HttpRequest(@NonNull String method,
                       @NonNull String path,
                       @Nullable Map<String, String> headers,
                       @Nullable String queryString,
                       @Nullable String clientIp) {
        this("req_" + System.currentTimeMillis(), method, path, headers, queryString, clientIp);
    }

    public HttpRequest(@NonNull String requestId,
                       @NonNull String method,
                       @NonNull String path,
                       @Nullable Map<String, String> headers,
                       @Nullable String queryString,
                       @Nullable String clientIp) {
        this.requestId = requestId;
        this.method = method.toUpperCase(Locale.ROOT);
        this.path = path;
        this.queryString = queryString != null ? queryString : "";
        this.clientIp = clientIp != null ? clientIp : "127.0.0.1";

        Map<String, String> headerMap = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null) {
                    headerMap.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                }
            }
        }
        this.headers = Collections.unmodifiableMap(headerMap);
    }

    @NonNull
    public String getRequestId() {
        return requestId;
    }

    @NonNull
    public String getMethod() {
        return method;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Nullable
    public String getHeader(@NonNull String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @NonNull
    public String getQueryString() {
        return queryString;
    }

    @NonNull
    public String getClientIp() {
        return clientIp;
    }
}
