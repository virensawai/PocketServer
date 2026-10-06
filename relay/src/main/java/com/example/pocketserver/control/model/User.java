package com.example.pocketserver.control.model;

/**
 * Model representing a registered user account in the Control Plane.
 */
public class User {

    private final String id;
    private final String email;
    private final String passwordHash;
    private final String salt;
    private final long createdAt;

    public User(String id, String email, String passwordHash, String salt, long createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
