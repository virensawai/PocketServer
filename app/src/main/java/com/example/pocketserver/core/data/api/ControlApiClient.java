package com.example.pocketserver.core.data.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.data.CredentialStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Production HTTP client for the PocketServer Control Plane REST API.
 */
public class ControlApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new GsonBuilder().create();

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final CredentialStore credentialStore;

    public ControlApiClient(@NonNull String baseUrl, @NonNull CredentialStore credentialStore) {
        this(baseUrl, credentialStore, createDefaultHttpClient());
    }

    public ControlApiClient(
            @NonNull String baseUrl,
            @NonNull CredentialStore credentialStore,
            @NonNull OkHttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.credentialStore = credentialStore;
        this.httpClient = httpClient;
    }

    private static OkHttpClient createDefaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // --- Authentication ---

    public JsonObject register(@NonNull String email, @NonNull String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        return post("/api/v1/auth/register", body, false);
    }

    public JsonObject login(@NonNull String email, @NonNull String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        return post("/api/v1/auth/login", body, false);
    }

    public JsonObject getMe() throws IOException {
        return get("/api/v1/auth/me", true);
    }

    // --- Device Registration ---

    public JsonObject registerDevice(
            @NonNull String deviceId,
            @Nullable String deviceName,
            @Nullable String model) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("deviceId", deviceId);
        body.put("deviceName", deviceName);
        body.put("model", model);
        return post("/api/v1/devices/register", body, true);
    }

    // --- Projects ---

    public JsonObject createProject(@NonNull String name, @Nullable String rootUriString) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("rootUriString", rootUriString);
        return post("/api/v1/projects", body, true);
    }

    public JsonObject getProjects() throws IOException {
        return get("/api/v1/projects", true);
    }

    // --- Deployments ---

    public JsonObject createDeployment(
            @NonNull String projectId,
            @NonNull String deviceId,
            @Nullable String requestedHostname,
            @Nullable String hostingMode,
            boolean spaFallbackEnabled) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", projectId);
        body.put("deviceId", deviceId);
        body.put("requestedHostname", requestedHostname);
        body.put("hostingMode", hostingMode);
        body.put("spaFallbackEnabled", spaFallbackEnabled);
        return post("/api/v1/deployments", body, true);
    }

    public JsonObject getDeployments() throws IOException {
        return get("/api/v1/deployments", true);
    }

    public JsonObject updateDeploymentStatus(
            @NonNull String deploymentId,
            @NonNull String status) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        return post("/api/v1/deployments/" + deploymentId + "/status", body, true);
    }

    public JsonObject refreshTunnelToken(
            @NonNull String deploymentId,
            @NonNull String deviceId) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("deviceId", deviceId);
        return post("/api/v1/deployments/" + deploymentId + "/token", body, true);
    }

    public boolean validateTunnelToken(@NonNull String token, @NonNull String deploymentId) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("deploymentId", deploymentId);
        JsonObject resp = post("/api/v1/tunnels/validate", body, false);
        return resp.has("valid") && resp.get("valid").getAsBoolean();
    }

    // --- HTTP Request Primitives ---

    private JsonObject post(String path, Object bodyData, boolean authenticated) throws IOException {
        String jsonBody = GSON.toJson(bodyData);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(jsonBody, JSON));

        if (authenticated) {
            String token = credentialStore.getAuthToken();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response);
        }
    }

    private JsonObject get(String path, boolean authenticated) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .get();

        if (authenticated) {
            String token = credentialStore.getAuthToken();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return parseResponse(response);
        }
    }

    private JsonObject parseResponse(Response response) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "{}";
        if (!response.isSuccessful()) {
            throw new IOException("API request failed with code " + response.code() + ": " + responseBody);
        }
        return JsonParser.parseString(responseBody).getAsJsonObject();
    }
}
