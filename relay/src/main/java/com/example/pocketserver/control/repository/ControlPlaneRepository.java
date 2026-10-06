package com.example.pocketserver.control.repository;

import com.example.pocketserver.control.model.Deployment;
import com.example.pocketserver.control.model.Device;
import com.example.pocketserver.control.model.Project;
import com.example.pocketserver.control.model.TunnelToken;
import com.example.pocketserver.control.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Control Plane metadata repository for user accounts, devices, projects,
 * deployments, and tunnel authentication tokens.
 */
public class ControlPlaneRepository {

    private final ConcurrentHashMap<String, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> usersByEmail = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Device> devicesById = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Project> projectsById = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Deployment> deploymentsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> hostnameToDeploymentId = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, TunnelToken> tunnelTokens = new ConcurrentHashMap<>();

    // --- User Operations ---

    public User saveUser(User user) {
        usersById.put(user.getId(), user);
        usersByEmail.put(user.getEmail().toLowerCase(Locale.ROOT), user);
        return user;
    }

    public User findUserById(String id) {
        return id != null ? usersById.get(id) : null;
    }

    public User findUserByEmail(String email) {
        return email != null ? usersByEmail.get(email.toLowerCase(Locale.ROOT)) : null;
    }

    // --- Device Operations ---

    public Device saveDevice(Device device) {
        devicesById.put(device.getId(), device);
        return device;
    }

    public Device findDeviceById(String id) {
        return id != null ? devicesById.get(id) : null;
    }

    public List<Device> findDevicesByUserId(String userId) {
        if (userId == null) return Collections.emptyList();
        List<Device> list = new ArrayList<>();
        for (Device device : devicesById.values()) {
            if (userId.equals(device.getUserId())) {
                list.add(device);
            }
        }
        return list;
    }

    // --- Project Operations ---

    public Project saveProject(Project project) {
        projectsById.put(project.getId(), project);
        return project;
    }

    public Project findProjectById(String id) {
        return id != null ? projectsById.get(id) : null;
    }

    public List<Project> findProjectsByUserId(String userId) {
        if (userId == null) return Collections.emptyList();
        List<Project> list = new ArrayList<>();
        for (Project project : projectsById.values()) {
            if (userId.equals(project.getUserId())) {
                list.add(project);
            }
        }
        return list;
    }

    // --- Deployment Operations ---

    public Deployment saveDeployment(Deployment deployment) {
        deploymentsById.put(deployment.getId(), deployment);
        if (deployment.getPublicHostname() != null) {
            hostnameToDeploymentId.put(deployment.getPublicHostname().toLowerCase(Locale.ROOT), deployment.getId());
        }
        return deployment;
    }

    public Deployment findDeploymentById(String id) {
        return id != null ? deploymentsById.get(id) : null;
    }

    public List<Deployment> findDeploymentsByProjectId(String projectId) {
        if (projectId == null) return Collections.emptyList();
        List<Deployment> list = new ArrayList<>();
        for (Deployment deployment : deploymentsById.values()) {
            if (projectId.equals(deployment.getProjectId())) {
                list.add(deployment);
            }
        }
        return list;
    }

    public List<Deployment> findDeploymentsByUserId(String userId) {
        if (userId == null) return Collections.emptyList();
        List<Deployment> list = new ArrayList<>();
        for (Deployment deployment : deploymentsById.values()) {
            if (userId.equals(deployment.getUserId())) {
                list.add(deployment);
            }
        }
        return list;
    }

    public boolean isHostnameTaken(String hostname) {
        if (hostname == null) return false;
        return hostnameToDeploymentId.containsKey(hostname.toLowerCase(Locale.ROOT));
    }

    // --- Tunnel Token Operations ---

    public TunnelToken saveTunnelToken(TunnelToken token) {
        tunnelTokens.put(token.getToken(), token);
        return token;
    }

    public TunnelToken findTunnelToken(String token) {
        if (token == null) return null;
        TunnelToken t = tunnelTokens.get(token);
        if (t != null && t.isExpired()) {
            tunnelTokens.remove(token);
            return null;
        }
        return t;
    }

    public boolean validateTunnelToken(String token, String deploymentId) {
        if (token == null || deploymentId == null) return false;
        TunnelToken t = findTunnelToken(token);
        return t != null && deploymentId.equals(t.getDeploymentId()) && !t.isExpired();
    }

    public void clear() {
        usersById.clear();
        usersByEmail.clear();
        devicesById.clear();
        projectsById.clear();
        deploymentsById.clear();
        hostnameToDeploymentId.clear();
        tunnelTokens.clear();
    }
}
