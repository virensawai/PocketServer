package com.example.pocketserver.core.storage;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight, immutable metadata record representing a resolved Storage Access Framework resource.
 */
public class SafResource {

    private final Uri uri;
    private final String name;
    private final String virtualRelativePath;
    private final long size;
    private final long lastModified;
    private final String mimeType;
    private final boolean isDirectory;
    private final boolean exists;

    public SafResource(@Nullable Uri uri,
                       @NonNull String name,
                       @NonNull String virtualRelativePath,
                       long size,
                       long lastModified,
                       @Nullable String mimeType,
                       boolean isDirectory,
                       boolean exists) {
        this.uri = uri;
        this.name = name;
        this.virtualRelativePath = virtualRelativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.mimeType = mimeType != null ? mimeType : "application/octet-stream";
        this.isDirectory = isDirectory;
        this.exists = exists;
    }

    @Nullable
    public Uri getUri() {
        return uri;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getVirtualRelativePath() {
        return virtualRelativePath;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean exists() {
        return exists;
    }

    @NonNull
    public static SafResource notFound(@NonNull String virtualRelativePath) {
        return new SafResource(null, "", virtualRelativePath, 0, 0, null, false, false);
    }
}
