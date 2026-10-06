package com.example.pocketserver.relay.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Text/JSON control frame exchanged with Android devices over the WebSocket reverse tunnel.
 * 100% interoperable with Android TunnelControlFrame.
 */
public class GatewayControlFrame {

    private static final Gson GSON = new GsonBuilder().create();

    private GatewayFrameType type;
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

    public GatewayControlFrame() {}

    // --- Getters & Setters ---

    public GatewayFrameType getType() {
        return type;
    }

    public void setType(GatewayFrameType type) {
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

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
        }
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public int getStatusCode() {
        return statusCode != null ? statusCode : 0;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public long getContentLength() {
        return contentLength != null ? contentLength : -1;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public int getErrorCode() {
        return errorCode != null ? errorCode : 0;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // --- Factory Helpers ---

    public static GatewayControlFrame registerOk(String publicUrl, String assignedHostname) {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.REGISTER_OK;
        frame.publicUrl = publicUrl;
        frame.hostname = assignedHostname;
        return frame;
    }

    public static GatewayControlFrame requestStart(
            String requestId,
            String method,
            String path,
            Map<String, String> headers,
            String queryString,
            String clientIp) {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.REQUEST_START;
        frame.requestId = requestId;
        frame.method = method;
        frame.path = path;
        frame.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        frame.queryString = queryString;
        frame.clientIp = clientIp;
        return frame;
    }

    public static GatewayControlFrame requestCancel(String requestId) {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.REQUEST_CANCEL;
        frame.requestId = requestId;
        return frame;
    }

    public static GatewayControlFrame ping() {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.PING;
        return frame;
    }

    public static GatewayControlFrame pong() {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.PONG;
        return frame;
    }

    public static GatewayControlFrame error(String requestId, int errorCode, String errorMessage) {
        GatewayControlFrame frame = new GatewayControlFrame();
        frame.type = GatewayFrameType.ERROR;
        frame.requestId = requestId;
        frame.errorCode = errorCode;
        frame.errorMessage = errorMessage;
        return frame;
    }

    // --- JSON Serialization ---

    public String toJson() {
        return GSON.toJson(this);
    }

    public static GatewayControlFrame fromJson(String json) {
        try {
            return GSON.fromJson(json, GatewayControlFrame.class);
        } catch (Exception e) {
            return null;
        }
    }
}
