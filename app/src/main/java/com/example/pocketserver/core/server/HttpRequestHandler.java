package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;

/**
 * Common interface for components that handle HTTP requests.
 * Used by both local embedded HTTP server adapters and reverse WebSocket tunnels.
 */
public interface HttpRequestHandler {

    /**
     * Dispatches and processes an incoming HTTP request, producing an HTTP response.
     *
     * @param request parsed HTTP request
     * @return HTTP response (status, headers, body stream)
     */
    @NonNull
    HttpResponse handle(@NonNull HttpRequest request);
}
