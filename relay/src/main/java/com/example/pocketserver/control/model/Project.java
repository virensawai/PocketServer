package com.example.pocketserver.control.model;

/**
 * Model representing a static website project in the Control Plane.
 */
public class Project {

    private final String id;
    private final String userId;
    private final String name;
    private final String rootUriString;
    private final long createdAt;

    public Project(String id, String userId, String name, String rootUriString, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.rootUriString = rootUriString;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getRootUriString() {
        return rootUriString;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
