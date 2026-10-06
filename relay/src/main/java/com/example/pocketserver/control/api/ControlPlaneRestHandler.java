package com.example.pocketserver.control.api;

import com.example.pocketserver.control.model.Deployment;
import com.example.pocketserver.control.model.Device;
import com.example.pocketserver.control.model.Project;
import com.example.pocketserver.control.model.TunnelToken;
import com.example.pocketserver.control.model.User;
import com.example.pocketserver.control.service.AuthService;
import com.example.pocketserver.control.service.DeploymentService;
import com.example.pocketserver.control.service.DeviceService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Netty inbound handler implementing the Control Plane REST API endpoints.
 */
@ChannelHandler.Sharable
public class ControlPlaneRestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final AuthService authService;
    private final DeviceService deviceService;
    private final DeploymentService deploymentService;

    public ControlPlaneRestHandler(
            AuthService authService,
            DeviceService deviceService,
            DeploymentService deploymentService) {
        this.authService = authService;
        this.deviceService = deviceService;
        this.deploymentService = deploymentService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        int queryIdx = uri.indexOf('?');
        String path = (queryIdx >= 0) ? uri.substring(0, queryIdx) : uri;
        HttpMethod method = request.method();

        // Handle CORS Pre-flight Options
        if (HttpMethod.OPTIONS.equals(method)) {
            handleCorsPreflight(ctx, request);
            return;
        }

        try {
            if (path.startsWith("/api/v1/auth/")) {
                handleAuthRoutes(ctx, request, path, method);
            } else if (path.startsWith("/api/v1/devices")) {
                handleDeviceRoutes(ctx, request, path, method);
            } else if (path.startsWith("/api/v1/projects")) {
                handleProjectRoutes(ctx, request, path, method);
            } else if (path.startsWith("/api/v1/deployments")) {
                handleDeploymentRoutes(ctx, request, path, method);
            } else if (path.equals("/api/v1/tunnels/validate")) {
                handleTunnelValidateRoute(ctx, request);
            } else {
                sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND,
                        Map.of("error", "Not Found", "message", "Endpoint " + path + " does not exist"));
            }
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, request, HttpResponseStatus.BAD_REQUEST,
                    Map.of("error", "Bad Request", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            sendJsonResponse(ctx, request, HttpResponseStatus.CONFLICT,
                    Map.of("error", "Conflict", "message", e.getMessage()));
        } catch (Exception e) {
            sendJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("error", "Internal Server Error", "message", e.getMessage()));
        }
    }

    // --- Route Handlers ---

    private void handleAuthRoutes(ChannelHandlerContext ctx, FullHttpRequest request, String path, HttpMethod method) {
        if ("/api/v1/auth/register".equals(path) && HttpMethod.POST.equals(method)) {
            JsonObject body = parseJsonBody(request);
            String email = getString(body, "email");
            String password = getString(body, "password");
            AuthService.AuthResult result = authService.register(email, password);

            Map<String, Object> resp = new HashMap<>();
            resp.put("user", result.getUser());
            resp.put("token", result.getToken());
            sendJsonResponse(ctx, request, HttpResponseStatus.CREATED, resp);
        } else if ("/api/v1/auth/login".equals(path) && HttpMethod.POST.equals(method)) {
            JsonObject body = parseJsonBody(request);
            String email = getString(body, "email");
            String password = getString(body, "password");
            AuthService.AuthResult result = authService.login(email, password);

            Map<String, Object> resp = new HashMap<>();
            resp.put("user", result.getUser());
            resp.put("token", result.getToken());
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, resp);
        } else if ("/api/v1/auth/me".equals(path) && HttpMethod.GET.equals(method)) {
            User user = requireAuthenticatedUser(request);
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("user", user));
        } else {
            sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Not Found"));
        }
    }

    private void handleDeviceRoutes(ChannelHandlerContext ctx, FullHttpRequest request, String path, HttpMethod method) {
        User user = requireAuthenticatedUser(request);

        if ("/api/v1/devices/register".equals(path) && HttpMethod.POST.equals(method)) {
            JsonObject body = parseJsonBody(request);
            String deviceId = getString(body, "deviceId");
            String deviceName = getString(body, "deviceName");
            String model = getString(body, "model");

            Device device = deviceService.registerDevice(user.getId(), deviceId, deviceName, model);
            sendJsonResponse(ctx, request, HttpResponseStatus.CREATED, Map.of("device", device));
        } else if ("/api/v1/devices".equals(path) && HttpMethod.GET.equals(method)) {
            List<Device> devices = deviceService.getDevicesForUser(user.getId());
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("devices", devices));
        } else {
            sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Not Found"));
        }
    }

    private void handleProjectRoutes(ChannelHandlerContext ctx, FullHttpRequest request, String path, HttpMethod method) {
        User user = requireAuthenticatedUser(request);

        if ("/api/v1/projects".equals(path)) {
            if (HttpMethod.POST.equals(method)) {
                JsonObject body = parseJsonBody(request);
                String name = getString(body, "name");
                String rootUri = getString(body, "rootUriString");

                Project project = deploymentService.createProject(user.getId(), name, rootUri);
                sendJsonResponse(ctx, request, HttpResponseStatus.CREATED, Map.of("project", project));
            } else if (HttpMethod.GET.equals(method)) {
                List<Project> projects = deploymentService.getProjectsForUser(user.getId());
                sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("projects", projects));
            } else {
                sendJsonResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, Map.of("error", "Method Not Allowed"));
            }
        } else if (path.startsWith("/api/v1/projects/") && HttpMethod.GET.equals(method)) {
            String projectId = path.substring("/api/v1/projects/".length());
            Project project = deploymentService.getProject(projectId);
            if (project == null || !project.getUserId().equals(user.getId())) {
                sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Project Not Found"));
                return;
            }
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("project", project));
        } else {
            sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Not Found"));
        }
    }

    private void handleDeploymentRoutes(ChannelHandlerContext ctx, FullHttpRequest request, String path, HttpMethod method) {
        User user = requireAuthenticatedUser(request);

        if ("/api/v1/deployments".equals(path)) {
            if (HttpMethod.POST.equals(method)) {
                JsonObject body = parseJsonBody(request);
                String projectId = getString(body, "projectId");
                String deviceId = getString(body, "deviceId");
                String requestedHostname = getString(body, "requestedHostname");
                String hostingMode = getString(body, "hostingMode");
                boolean spaFallback = body.has("spaFallbackEnabled") && body.get("spaFallbackEnabled").getAsBoolean();

                DeploymentService.DeploymentResult result = deploymentService.createDeployment(
                        user.getId(), projectId, deviceId, requestedHostname, hostingMode, spaFallback
                );

                Map<String, Object> resp = new HashMap<>();
                resp.put("deployment", result.getDeployment());
                resp.put("tunnelToken", result.getTunnelToken());
                sendJsonResponse(ctx, request, HttpResponseStatus.CREATED, resp);
            } else if (HttpMethod.GET.equals(method)) {
                List<Deployment> deployments = deploymentService.getDeploymentsForUser(user.getId());
                sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("deployments", deployments));
            } else {
                sendJsonResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, Map.of("error", "Method Not Allowed"));
            }
        } else if (path.matches("^/api/v1/deployments/[^/]+/status$") && HttpMethod.POST.equals(method)) {
            String[] segments = path.split("/");
            String deploymentId = segments[4];
            JsonObject body = parseJsonBody(request);
            String status = getString(body, "status");

            Deployment deployment = deploymentService.updateDeploymentStatus(deploymentId, status);
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("deployment", deployment));
        } else if (path.matches("^/api/v1/deployments/[^/]+/token$") && HttpMethod.POST.equals(method)) {
            String[] segments = path.split("/");
            String deploymentId = segments[4];
            JsonObject body = parseJsonBody(request);
            String deviceId = getString(body, "deviceId");

            TunnelToken token = deploymentService.refreshTunnelToken(deploymentId, deviceId);
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("tunnelToken", token));
        } else if (path.startsWith("/api/v1/deployments/") && HttpMethod.GET.equals(method)) {
            String deploymentId = path.substring("/api/v1/deployments/".length());
            Deployment deployment = deploymentService.getDeployment(deploymentId);
            if (deployment == null || !deployment.getUserId().equals(user.getId())) {
                sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Deployment Not Found"));
                return;
            }
            sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("deployment", deployment));
        } else {
            sendJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, Map.of("error", "Not Found"));
        }
    }

    private void handleTunnelValidateRoute(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!HttpMethod.POST.equals(request.method())) {
            sendJsonResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, Map.of("error", "Method Not Allowed"));
            return;
        }

        JsonObject body = parseJsonBody(request);
        String token = getString(body, "token");
        String deploymentId = getString(body, "deploymentId");

        boolean valid = deploymentService.validateTunnelToken(token, deploymentId);
        sendJsonResponse(ctx, request, HttpResponseStatus.OK, Map.of("valid", valid));
    }

    // --- Helpers ---

    private User requireAuthenticatedUser(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }
        User user = authService.authenticateToken(authHeader);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired session token");
        }
        return user;
    }

    private JsonObject parseJsonBody(FullHttpRequest request) {
        String content = request.content().toString(StandardCharsets.UTF_8);
        if (content == null || content.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON request body");
        }
    }

    private String getString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    private void handleCorsPreflight(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        response.headers().set("Access-Control-Max-Age", "86400");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, Object data) {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set("Access-Control-Allow-Origin", "*");

        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (!keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        }
    }
}
