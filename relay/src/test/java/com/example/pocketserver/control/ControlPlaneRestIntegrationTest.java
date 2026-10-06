package com.example.pocketserver.control;

import com.example.pocketserver.control.api.ControlPlaneServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ControlPlaneRestIntegrationTest {

    private ControlPlaneServer server;
    private int port;
    private OkHttpClient client;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Before
    public void setUp() throws Exception {
        server = new ControlPlaneServer(0); // dynamic port
        server.start();
        port = server.getPort();

        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testFullAccountAndDeploymentLifecycle() throws Exception {
        String baseUrl = "http://localhost:" + port;

        // 1. Register Account
        String regJson = "{\"email\":\"tester@pocketserver.dev\",\"password\":\"pass1234\"}";
        Request regReq = new Request.Builder()
                .url(baseUrl + "/api/v1/auth/register")
                .post(RequestBody.create(regJson, JSON))
                .build();

        String token;
        String userId;
        try (Response regResp = client.newCall(regReq).execute()) {
            assertEquals(201, regResp.code());
            JsonObject json = JsonParser.parseString(regResp.body().string()).getAsJsonObject();
            token = json.get("token").getAsString();
            userId = json.getAsJsonObject("user").get("id").getAsString();
            assertNotNull(token);
            assertNotNull(userId);
        }

        // 2. Fetch User Profile with Bearer Token
        Request meReq = new Request.Builder()
                .url(baseUrl + "/api/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response meResp = client.newCall(meReq).execute()) {
            assertEquals(200, meResp.code());
            JsonObject json = JsonParser.parseString(meResp.body().string()).getAsJsonObject();
            assertEquals("tester@pocketserver.dev", json.getAsJsonObject("user").get("email").getAsString());
        }

        // 3. Register Android Device
        String devJson = "{\"deviceId\":\"dev_pixel8_1\",\"deviceName\":\"Pixel 8 Pro\",\"model\":\"Google Pixel 8 Pro\"}";
        Request devReq = new Request.Builder()
                .url(baseUrl + "/api/v1/devices/register")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(devJson, JSON))
                .build();

        String deviceId;
        try (Response devResp = client.newCall(devReq).execute()) {
            assertEquals(201, devResp.code());
            JsonObject json = JsonParser.parseString(devResp.body().string()).getAsJsonObject();
            deviceId = json.getAsJsonObject("device").get("id").getAsString();
            assertEquals("dev_pixel8_1", deviceId);
        }

        // 4. Create Project
        String projJson = "{\"name\":\"My Vite Blog\",\"rootUriString\":\"content://com.android.externalstorage.documents/tree/primary%3ABlog\"}";
        Request projReq = new Request.Builder()
                .url(baseUrl + "/api/v1/projects")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(projJson, JSON))
                .build();

        String projectId;
        try (Response projResp = client.newCall(projReq).execute()) {
            assertEquals(201, projResp.code());
            JsonObject json = JsonParser.parseString(projResp.body().string()).getAsJsonObject();
            projectId = json.getAsJsonObject("project").get("id").getAsString();
            assertEquals("My Vite Blog", json.getAsJsonObject("project").get("name").getAsString());
        }

        // 5. Create Deployment and obtain Tunnel Token
        String depJson = "{\"projectId\":\"" + projectId + "\",\"deviceId\":\"" + deviceId +
                "\",\"requestedHostname\":\"my-vite-blog\",\"hostingMode\":\"PUBLIC\",\"spaFallbackEnabled\":true}";
        Request depReq = new Request.Builder()
                .url(baseUrl + "/api/v1/deployments")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(depJson, JSON))
                .build();

        String deploymentId;
        String tunnelToken;
        String assignedHostname;
        try (Response depResp = client.newCall(depReq).execute()) {
            assertEquals(201, depResp.code());
            JsonObject json = JsonParser.parseString(depResp.body().string()).getAsJsonObject();
            deploymentId = json.getAsJsonObject("deployment").get("id").getAsString();
            assignedHostname = json.getAsJsonObject("deployment").get("publicHostname").getAsString();
            tunnelToken = json.getAsJsonObject("tunnelToken").get("token").getAsString();

            assertEquals("my-vite-blog", assignedHostname);
            assertTrue(tunnelToken.startsWith("ttok_"));
        }

        // 6. Validate Tunnel Token (Gateway validation endpoint)
        String valJson = "{\"token\":\"" + tunnelToken + "\",\"deploymentId\":\"" + deploymentId + "\"}";
        Request valReq = new Request.Builder()
                .url(baseUrl + "/api/v1/tunnels/validate")
                .post(RequestBody.create(valJson, JSON))
                .build();

        try (Response valResp = client.newCall(valReq).execute()) {
            assertEquals(200, valResp.code());
            JsonObject json = JsonParser.parseString(valResp.body().string()).getAsJsonObject();
            assertTrue(json.get("valid").getAsBoolean());
        }

        // 7. Update Deployment Status to LIVE
        String statusJson = "{\"status\":\"LIVE\"}";
        Request statusReq = new Request.Builder()
                .url(baseUrl + "/api/v1/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(statusJson, JSON))
                .build();

        try (Response statusResp = client.newCall(statusReq).execute()) {
            assertEquals(200, statusResp.code());
            JsonObject json = JsonParser.parseString(statusResp.body().string()).getAsJsonObject();
            assertEquals("LIVE", json.getAsJsonObject("deployment").get("status").getAsString());
        }

        // 8. Refresh Tunnel Token
        String refJson = "{\"deviceId\":\"" + deviceId + "\"}";
        Request refReq = new Request.Builder()
                .url(baseUrl + "/api/v1/deployments/" + deploymentId + "/token")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(refJson, JSON))
                .build();

        try (Response refResp = client.newCall(refReq).execute()) {
            assertEquals(200, refResp.code());
            JsonObject json = JsonParser.parseString(refResp.body().string()).getAsJsonObject();
            String newToken = json.getAsJsonObject("tunnelToken").get("token").getAsString();
            assertNotNull(newToken);
            assertTrue(newToken.startsWith("ttok_"));
        }
    }
}
