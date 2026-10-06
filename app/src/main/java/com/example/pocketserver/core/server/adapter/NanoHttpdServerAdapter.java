package com.example.pocketserver.core.server.adapter;

import androidx.annotation.NonNull;
import com.example.pocketserver.core.server.HttpRequest;
import com.example.pocketserver.core.server.HttpRequestHandler;
import com.example.pocketserver.core.server.HttpResponse;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.Map;

/**
 * Transport adapter connecting local incoming HTTP socket requests (NanoHTTPD)
 * to the decoupled {@link HttpRequestHandler} architecture.
 */
public class NanoHttpdServerAdapter extends NanoHTTPD {

    private final HttpRequestHandler requestHandler;

    public NanoHttpdServerAdapter(String hostname, int port, @NonNull HttpRequestHandler requestHandler) {
        super(hostname, port);
        this.requestHandler = requestHandler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String uri = session.getUri();
        Map<String, String> headers = session.getHeaders();
        String queryString = session.getQueryParameterString();
        String remoteIp = session.getRemoteIpAddress();

        HttpRequest request = new HttpRequest(
                method,
                uri,
                headers,
                queryString,
                remoteIp
        );

        HttpResponse appResponse = requestHandler.handle(request);

        Response.IStatus status = new CustomHttpStatus(appResponse.getStatusCode(), appResponse.getStatusMessage());
        String contentType = appResponse.getHeader("Content-Type");
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        Response nanoResponse;
        if (appResponse.hasBody() && appResponse.getBodyStream() != null) {
            if (appResponse.getContentLength() >= 0) {
                nanoResponse = newFixedLengthResponse(
                        status,
                        contentType,
                        appResponse.getBodyStream(),
                        appResponse.getContentLength()
                );
            } else {
                nanoResponse = newChunkedResponse(status, contentType, appResponse.getBodyStream());
            }
        } else {
            nanoResponse = newFixedLengthResponse(status, contentType, "");
        }

        for (Map.Entry<String, String> header : appResponse.getHeaders().entrySet()) {
            if (!header.getKey().equalsIgnoreCase("Content-Type") &&
                !header.getKey().equalsIgnoreCase("Content-Length")) {
                nanoResponse.addHeader(header.getKey(), header.getValue());
            }
        }

        return nanoResponse;
    }

    private static class CustomHttpStatus implements Response.IStatus {
        private final int code;
        private final String description;

        public CustomHttpStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        public String getDescription() {
            return "" + code + " " + description;
        }

        @Override
        public int getRequestStatus() {
            return code;
        }
    }
}
