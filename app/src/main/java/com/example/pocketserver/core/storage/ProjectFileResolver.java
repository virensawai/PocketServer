package com.example.pocketserver.core.storage;

import android.content.res.AssetFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for resolving virtual paths to readable files.
 * Decouples the static content engine from specific storage technologies (SAF, mock assets, testing).
 */
public interface ProjectFileResolver {

    /**
     * Resolves a sanitized virtual relative path (e.g. "index.html", "assets/app.js")
     * to a {@link SafResource} metadata record.
     *
     * @param sanitizedPath clean, relative virtual path
     * @return resource metadata, or null if the resource does not exist
     */
    @Nullable
    SafResource resolve(@NonNull String sanitizedPath);

    /**
     * Opens an input stream for reading the resource.
     *
     * @param resource valid existing resource
     * @return open InputStream
     * @throws IOException on reading failure or permission revocation
     */
    @NonNull
    InputStream openInputStream(@NonNull SafResource resource) throws IOException;

    /**
     * Opens an AssetFileDescriptor for range skipping if supported by the provider,
     * or null if not supported.
     */
    @Nullable
    AssetFileDescriptor openAssetFileDescriptor(@NonNull SafResource resource);
}
