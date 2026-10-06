package com.example.pocketserver.core.storage;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.core.server.MimeTypes;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production hybrid Storage Access Framework file resolver.
 * Uses an on-demand segment-by-segment traversal paired with an in-memory
 * LRU cache and short-lived negative cache to avoid crawling massive directory trees.
 */
public class HybridSafFileResolver implements ProjectFileResolver {

    private final Context context;
    private final Uri rootTreeUri;
    private final DocumentFile rootDocFile;
    private final LruCache<String, SafResource> lruCache;
    private final Map<String, Long> negativeCache = new ConcurrentHashMap<>();

    public HybridSafFileResolver(@NonNull Context context, @NonNull Uri rootTreeUri) {
        this.context = context.getApplicationContext();
        this.rootTreeUri = rootTreeUri;
        if ("file".equalsIgnoreCase(rootTreeUri.getScheme()) && rootTreeUri.getPath() != null) {
            this.rootDocFile = DocumentFile.fromFile(new java.io.File(rootTreeUri.getPath()));
        } else {
            this.rootDocFile = DocumentFile.fromTreeUri(context, rootTreeUri);
        }
        this.lruCache = new LruCache<>(LimitsConfig.SAF_LRU_CACHE_CAPACITY);
    }

    @Nullable
    @Override
    public SafResource resolve(@NonNull String sanitizedPath) {
        String path = (sanitizedPath.isEmpty() || sanitizedPath.equals("/")) ? "index.html" : sanitizedPath;

        // 1. Check negative cache for recently missing files
        Long negativeTimestamp = negativeCache.get(path);
        if (negativeTimestamp != null) {
            if (System.currentTimeMillis() - negativeTimestamp < LimitsConfig.NEGATIVE_CACHE_TTL_MS) {
                return null;
            } else {
                negativeCache.remove(path);
            }
        }

        // 2. Check LRU cache for previous hit
        synchronized (lruCache) {
            SafResource cached = lruCache.get(path);
            if (cached != null) {
                return cached;
            }
        }

        // 3. Traverse on demand segment by segment
        if (rootDocFile == null || !rootDocFile.exists()) {
            return null;
        }

        String[] segments = path.split("/");
        DocumentFile currentDoc = rootDocFile;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;

            DocumentFile child = currentDoc.findFile(segment);
            if (child == null || !child.exists()) {
                negativeCache.put(path, System.currentTimeMillis());
                return null;
            }

            boolean isLast = (i == segments.length - 1);
            if (!isLast) {
                if (!child.isDirectory()) {
                    negativeCache.put(path, System.currentTimeMillis());
                    return null;
                }
                currentDoc = child;
            } else {
                currentDoc = child;
            }
        }

        if (currentDoc.isDirectory()) {
            // If path resolved to a directory, try resolving index.html inside it
            DocumentFile indexFile = currentDoc.findFile("index.html");
            if (indexFile != null && indexFile.exists() && indexFile.isFile()) {
                currentDoc = indexFile;
                path = path.endsWith("/") ? (path + "index.html") : (path + "/index.html");
            } else {
                negativeCache.put(path, System.currentTimeMillis());
                return null;
            }
        }

        // Extract metadata
        Uri fileUri = currentDoc.getUri();
        String name = currentDoc.getName() != null ? currentDoc.getName() : path;
        long size = currentDoc.length();
        long lastModified = currentDoc.lastModified();
        String mimeType = MimeTypes.getMimeType(path);

        SafResource resource = new SafResource(
                fileUri,
                name,
                path,
                size,
                lastModified,
                mimeType,
                false,
                true
        );

        synchronized (lruCache) {
            lruCache.put(path, resource);
        }

        return resource;
    }

    @NonNull
    @Override
    public InputStream openInputStream(@NonNull SafResource resource) throws IOException {
        if ("file".equalsIgnoreCase(resource.getUri().getScheme()) && resource.getUri().getPath() != null) {
            return new java.io.FileInputStream(new java.io.File(resource.getUri().getPath()));
        }
        InputStream stream = context.getContentResolver().openInputStream(resource.getUri());
        if (stream == null) {
            throw new FileNotFoundException("ContentResolver could not open stream for URI: " + resource.getUri());
        }
        return stream;
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFileDescriptor(@NonNull SafResource resource) {
        try {
            if ("file".equalsIgnoreCase(resource.getUri().getScheme()) && resource.getUri().getPath() != null) {
                java.io.File file = new java.io.File(resource.getUri().getPath());
                android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                return new AssetFileDescriptor(pfd, 0, file.length());
            }
            return context.getContentResolver().openAssetFileDescriptor(resource.getUri(), "r");
        } catch (Exception e) {
            return null;
        }
    }

    public Uri getRootTreeUri() {
        return rootTreeUri;
    }

    public void clearCache() {
        synchronized (lruCache) {
            lruCache.evictAll();
        }
        negativeCache.clear();
    }
}
