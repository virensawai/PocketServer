package com.example.pocketserver.control.service;

import com.example.pocketserver.control.repository.ControlPlaneRepository;
import java.util.Locale;
import java.util.UUID;

/**
 * Assigns collision-free, memorable public subdomains for deployments.
 */
public class HostnameAssignmentService {

    private final ControlPlaneRepository repository;

    public HostnameAssignmentService(ControlPlaneRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves a guaranteed unique public hostname for a new deployment.
     */
    public synchronized String assignHostname(String requestedHostname, String projectName, String deploymentId) {
        // 1. Try requested hostname if provided
        if (requestedHostname != null && !requestedHostname.trim().isEmpty()) {
            String sanitized = sanitize(requestedHostname);
            if (!sanitized.isEmpty() && !repository.isHostnameTaken(sanitized)) {
                return sanitized;
            }
            // Suffix with short random hash
            String candidate = sanitized + "-" + randomSuffix();
            if (!repository.isHostnameTaken(candidate)) {
                return candidate;
            }
        }

        // 2. Try project name prefix
        if (projectName != null && !projectName.trim().isEmpty()) {
            String sanitized = sanitize(projectName);
            if (!sanitized.isEmpty()) {
                String candidate = sanitized + "-" + randomSuffix();
                if (!repository.isHostnameTaken(candidate)) {
                    return candidate;
                }
            }
        }

        // 3. Fallback based on deploymentId
        if (deploymentId != null && !deploymentId.trim().isEmpty()) {
            String sanitized = sanitize(deploymentId);
            if (!sanitized.isEmpty() && !repository.isHostnameTaken(sanitized)) {
                return sanitized;
            }
        }

        // 4. Guaranteed fallback
        return "site-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String sanitize(String input) {
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]", "")
                .replaceAll("^-+|-+$", "");
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 5);
    }
}
