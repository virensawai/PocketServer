package com.example.pocketserver.control.service;

import com.example.pocketserver.control.model.Deployment;
import com.example.pocketserver.control.model.Project;
import com.example.pocketserver.control.model.TunnelToken;
import com.example.pocketserver.control.repository.ControlPlaneRepository;
import java.util.List;
import java.util.UUID;

/**
 * Service managing project repositories, deployment state transitions,
 * and 24-hour ephemeral tunnel token lifecycles.
 */
public class DeploymentService {

    public static final long TUNNEL_TOKEN_TTL_MS = 24L * 60 * 60 * 1000; // 24 hours

    private final ControlPlaneRepository repository;
    private final HostnameAssignmentService hostnameService;

    public DeploymentService(ControlPlaneRepository repository, HostnameAssignmentService hostnameService) {
        this.repository = repository;
        this.hostnameService = hostnameService;
    }

    public static class DeploymentResult {
        private final Deployment deployment;
        private final TunnelToken tunnelToken;

        public DeploymentResult(Deployment deployment, TunnelToken tunnelToken) {
            this.deployment = deployment;
            this.tunnelToken = tunnelToken;
        }

        public Deployment getDeployment() {
            return deployment;
        }

        public TunnelToken getTunnelToken() {
            return tunnelToken;
        }
    }

    // --- Project Management ---

    public Project createProject(String userId, String name, String rootUriString) {
        if (userId == null || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("userId and project name are required");
        }
        String projectId = "proj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Project project = new Project(projectId, userId, name.trim(), rootUriString, System.currentTimeMillis());
        return repository.saveProject(project);
    }

    public Project getProject(String projectId) {
        return repository.findProjectById(projectId);
    }

    public List<Project> getProjectsForUser(String userId) {
        return repository.findProjectsByUserId(userId);
    }

    // --- Deployment Management ---

    public DeploymentResult createDeployment(
            String userId,
            String projectId,
            String deviceId,
            String requestedHostname,
            String hostingMode,
            boolean spaFallbackEnabled) {
        Project project = repository.findProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        if (!project.getUserId().equals(userId)) {
            throw new IllegalStateException("User does not own project: " + projectId);
        }

        String safeDeviceId = (deviceId != null && !deviceId.trim().isEmpty()) ? deviceId.trim() : "dev_default";
        String deploymentId = "dep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String assignedHostname = hostnameService.assignHostname(requestedHostname, project.getName(), deploymentId);

        List<Deployment> existing = repository.findDeploymentsByProjectId(projectId);
        int version = existing.size() + 1;

        String safeMode = ("LOCAL".equalsIgnoreCase(hostingMode)) ? "LOCAL" : "PUBLIC";

        Deployment deployment = new Deployment(
                deploymentId,
                projectId,
                safeDeviceId,
                userId,
                version,
                safeMode,
                assignedHostname,
                "STOPPED",
                spaFallbackEnabled,
                System.currentTimeMillis()
        );
        repository.saveDeployment(deployment);

        // Issue 24-hour ephemeral tunnel token
        TunnelToken tunnelToken = issueTunnelToken(deploymentId, safeDeviceId);

        return new DeploymentResult(deployment, tunnelToken);
    }

    public Deployment getDeployment(String deploymentId) {
        return repository.findDeploymentById(deploymentId);
    }

    public List<Deployment> getDeploymentsForUser(String userId) {
        return repository.findDeploymentsByUserId(userId);
    }

    public List<Deployment> getDeploymentsForProject(String projectId) {
        return repository.findDeploymentsByProjectId(projectId);
    }

    public Deployment updateDeploymentStatus(String deploymentId, String newStatus) {
        Deployment deployment = repository.findDeploymentById(deploymentId);
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment not found: " + deploymentId);
        }
        deployment.setStatus(newStatus);
        if ("STOPPED".equalsIgnoreCase(newStatus) || "FAILED".equalsIgnoreCase(newStatus)) {
            deployment.setStoppedAt(System.currentTimeMillis());
        }
        return deployment;
    }

    public TunnelToken refreshTunnelToken(String deploymentId, String deviceId) {
        Deployment deployment = repository.findDeploymentById(deploymentId);
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment not found: " + deploymentId);
        }
        return issueTunnelToken(deploymentId, deviceId);
    }

    public boolean validateTunnelToken(String token, String deploymentId) {
        return repository.validateTunnelToken(token, deploymentId);
    }

    private TunnelToken issueTunnelToken(String deploymentId, String deviceId) {
        String tokenString = "ttok_" + UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        long expiresAt = now + TUNNEL_TOKEN_TTL_MS;
        TunnelToken tunnelToken = new TunnelToken(tokenString, deploymentId, deviceId, expiresAt, now);
        return repository.saveTunnelToken(tunnelToken);
    }
}
