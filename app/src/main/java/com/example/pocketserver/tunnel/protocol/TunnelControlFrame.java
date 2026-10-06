package com.example.pocketserver.tunnel.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Text/JSON control frame exchanged over the multiplexed WebSocket reverse tunnel.
 * Encompasses handshakes, request starts, response headers, stream ends, and cancellations.
 */
public class TunnelControlFrame {

    private static final Gson GSON = new GsonBuilder().create();

    private FrameType type;
    private String requestId;

    // HTTP Request Metadata
    private String method;
    private String path;
    private Map<String, String> headers;
    private String queryString;
    private String clientIp;

    // HTTP Response Metadata
    private Integer statusCode;
    private String statusMessage;
    private Long contentLength;

    // Handshake & Authentication Metadata
    private String deploymentId;
    private String projectId;
    private String token;
    private String hostname;
    private String publicUrl;

    // Error & Diagnostics
    private Integer errorCode;
    private String errorMessage;

    public TunnelControlFrame() {}

    // --- Getters & Setters ---

    public FrameType getType() {
        return type;
    }

    public void setType(FrameType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return headers != null ? headers : Collections.emptyMap();
    }

    public String getQueryString() {
        return queryString;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getStatusCode() {
        return statusCode != null ? statusCode : 0;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public long getContentLength() {
        return contentLength != null ? contentLength : -1;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getToken() {
        return token;
    }

    public String getHostname() {
        return hostname;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public int getErrorCode() {
        return errorCode != null ? errorCode : 0;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // --- Factory Helpers ---

    @NonNull
    public static TunnelControlFrame register(
            @NonNull String deploymentId,
            @NonNull String projectId,
            @Nullable String token,
            @Nullable String hostname) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.REGISTER;
        frame.deploymentId = deploymentId;
        frame.projectId = projectId;
        frame.token = token;
        frame.hostname = hostname;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame registerOk(
            @NonNull String publicUrl,
            @NonNull String assignedHostname) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.REGISTER_OK;
        frame.publicUrl = publicUrl;
        frame.hostname = assignedHostname;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame requestStart(
            @NonNull String requestId,
            @NonNull String method,
            @NonNull String path,
            @Nullable Map<String, String> headers,
            @Nullable String queryString,
            @Nullable String clientIp) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.REQUEST_START;
        frame.requestId = requestId;
        frame.method = method;
        frame.path = path;
        frame.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        frame.queryString = queryString;
        frame.clientIp = clientIp;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame responseStart(
            @NonNull String requestId,
            int statusCode,
            @NonNull String statusMessage,
            @Nullable Map<String, String> headers,
            long contentLength) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.RESPONSE_START;
        frame.requestId = requestId;
        frame.statusCode = statusCode;
        frame.statusMessage = statusMessage;
        frame.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        frame.contentLength = contentLength;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame streamEnd(@NonNull String requestId) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.STREAM_END;
        frame.requestId = requestId;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame requestCancel(@NonNull String requestId) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.REQUEST_CANCEL;
        frame.requestId = requestId;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame ping() {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.PING;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame pong() {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.PONG;
        return frame;
    }

    @NonNull
    public static TunnelControlFrame error(
            @Nullable String requestId,
            int errorCode,
            @NonNull String errorMessage) {
        TunnelControlFrame frame = new TunnelControlFrame();
        frame.type = FrameType.ERROR;
        frame.requestId = requestId;
        frame.errorCode = errorCode;
        frame.errorMessage = errorMessage;
        return frame;
    }

    // --- JSON Serialization ---

    @NonNull
    public String toJson() {
        return GSON.toJson(this);
    }

    @Nullable
    public static TunnelControlFrame fromJson(@NonNull String json) {
        try {
            return GSON.fromJson(json, TunnelControlFrame.class);
        } catch (Exception e) {
            return null;
        }
    }
}
