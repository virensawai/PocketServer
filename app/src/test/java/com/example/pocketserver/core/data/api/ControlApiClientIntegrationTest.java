package com.example.pocketserver.core.data.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pocketserver.control.api.ControlPlaneServer;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.core.data.FakeSharedPreferences;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ControlApiClientIntegrationTest {

    private ControlPlaneServer controlServer;
    private int port;
    private CredentialStore credentialStore;
    private ControlApiClient apiClient;

    @Before
    public void setUp() throws Exception {
        controlServer = new ControlPlaneServer(0);
        controlServer.start();
        port = controlServer.getPort();

        FakeSharedPreferences fakePrefs = new FakeSharedPreferences();
        credentialStore = new CredentialStore(fakePrefs);
        apiClient = new ControlApiClient("http://127.0.0.1:" + port, credentialStore);
    }

    @After
    public void tearDown() {
        if (controlServer != null) {
            controlServer.stop();
        }
    }

    @Test
    public void testFullControlApiClientFlow() throws Exception {
        // 1. Register Account via Android Client
        JsonObject regResp = apiClient.register("androiddev@pocketserver.dev", "mypassword123");
        assertNotNull(regResp);
        String token = regResp.get("token").getAsString();
        String userId = regResp.getAsJsonObject("user").get("id").getAsString();
        assertNotNull(token);
        assertNotNull(userId);

        // Save session in CredentialStore
        credentialStore.saveSession(token, userId, "androiddev@pocketserver.dev");
        assertTrue(credentialStore.isLoggedIn());

        // 2. Fetch User Profile
        JsonObject meResp = apiClient.getMe();
        assertEquals("androiddev@pocketserver.dev", meResp.getAsJsonObject("user").get("email").getAsString());

        // 3. Register Android Device
        JsonObject devResp = apiClient.registerDevice("dev_android_tablet_1", "Pixel Tablet", "Google");
        assertEquals("dev_android_tablet_1", devResp.getAsJsonObject("device").get("id").getAsString());

        // 4. Create Project
        JsonObject projResp = apiClient.createProject("DocSite", "content://tree/documents/docs");
        String projectId = projResp.getAsJsonObject("project").get("id").getAsString();
        assertEquals("DocSite", projResp.getAsJsonObject("project").get("name").getAsString());

        // 5. Query Projects
        JsonObject projectsResp = apiClient.getProjects();
        JsonArray projects = projectsResp.getAsJsonArray("projects");
        assertEquals(1, projects.size());

        // 6. Create Deployment and obtain 24h Tunnel Token
        JsonObject depResp = apiClient.createDeployment(
                projectId, "dev_android_tablet_1", "doc-site", "PUBLIC", true
        );
        String deploymentId = depResp.getAsJsonObject("deployment").get("id").getAsString();
        String tunnelToken = depResp.getAsJsonObject("tunnelToken").get("token").getAsString();
        String assignedHostname = depResp.getAsJsonObject("deployment").get("publicHostname").getAsString();

        assertNotNull(deploymentId);
        assertNotNull(tunnelToken);
        assertEquals("doc-site", assignedHostname);

        // Cache tunnel token locally
        credentialStore.saveTunnelToken(deploymentId, tunnelToken);
        assertEquals(tunnelToken, credentialStore.getTunnelToken(deploymentId));

        // 7. Validate Tunnel Token
        boolean isValid = apiClient.validateTunnelToken(tunnelToken, deploymentId);
        assertTrue("Tunnel token should be valid", isValid);

        // 8. Update Deployment Status
        JsonObject statusResp = apiClient.updateDeploymentStatus(deploymentId, "LIVE");
        assertEquals("LIVE", statusResp.getAsJsonObject("deployment").get("status").getAsString());

        // 9. Query Deployments
        JsonObject depsResp = apiClient.getDeployments();
        JsonArray deployments = depsResp.getAsJsonArray("deployments");
        assertEquals(1, deployments.size());

        // 10. Refresh Tunnel Token
        JsonObject refResp = apiClient.refreshTunnelToken(deploymentId, "dev_android_tablet_1");
        String refreshedToken = refResp.getAsJsonObject("tunnelToken").get("token").getAsString();
        assertNotNull(refreshedToken);
        assertTrue(apiClient.validateTunnelToken(refreshedToken, deploymentId));
    }
}
