package com.example.pocketserver.core.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for persisted website projects on the Android device.
 */
@Entity(tableName = "projects")
public class ProjectEntity {

    @PrimaryKey
    @NonNull
    private String projectId;
    private String userId;
    private String name;
    private String rootUriString;
    private long createdAt;

    public ProjectEntity(
            @NonNull String projectId,
            String userId,
            String name,
            String rootUriString,
            long createdAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.name = name;
        this.rootUriString = rootUriString;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(@NonNull String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootUriString() {
        return rootUriString;
    }

    public void setRootUriString(String rootUriString) {
        this.rootUriString = rootUriString;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
