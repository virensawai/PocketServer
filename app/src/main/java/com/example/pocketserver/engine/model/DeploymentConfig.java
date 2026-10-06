package com.example.pocketserver.engine.model;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.core.config.LimitsConfig;
import java.io.Serializable;

/**
 * Immutable configuration parameters specifying how a project deployment
 * should be initialized, bound, and hosted.
 */
public class DeploymentConfig implements Serializable {

    public static final int DEFAULT_LOCAL_PORT = 8080;
    public static final long DEFAULT_DATA_WARNING_BYTES = 500L * 1024 * 1024;      // 500 MB
    public static final long DEFAULT_DATA_LIMIT_BYTES = 2L * 1024 * 1024 * 1024;     // 2 GB

    private final String projectId;
    private final String projectName;
    private final String rootUriString;
    private final HostingMode hostingMode;
    private final int localPort;
    private final boolean spaFallbackEnabled;
    private final String publicHostname;
    private final NetworkPolicy networkPolicy;
    private final boolean keepAwakeWhileLocked;
    private final long dataWarningThresholdBytes;
    private final long dataLimitThresholdBytes;

    private DeploymentConfig(Builder builder) {
        this.projectId = builder.projectId;
        this.projectName = builder.projectName;
        this.rootUriString = builder.rootUri != null ? builder.rootUri.toString() : "";
        this.hostingMode = builder.hostingMode != null ? builder.hostingMode : HostingMode.LOCAL;
        this.localPort = builder.localPort > 0 ? builder.localPort : DEFAULT_LOCAL_PORT;
        this.spaFallbackEnabled = builder.spaFallbackEnabled;
        this.publicHostname = builder.publicHostname;
        this.networkPolicy = builder.networkPolicy != null ? builder.networkPolicy : NetworkPolicy.WIFI_AND_MOBILE;
        this.keepAwakeWhileLocked = builder.keepAwakeWhileLocked;
        this.dataWarningThresholdBytes = builder.dataWarningThresholdBytes > 0 ?
                builder.dataWarningThresholdBytes : DEFAULT_DATA_WARNING_BYTES;
        this.dataLimitThresholdBytes = builder.dataLimitThresholdBytes > 0 ?
                builder.dataLimitThresholdBytes : DEFAULT_DATA_LIMIT_BYTES;
    }

    @NonNull
    public String getProjectId() {
        return projectId;
    }

    @NonNull
    public String getProjectName() {
        return projectName;
    }

    @Nullable
    public Uri getRootUri() {
        return rootUriString.isEmpty() ? null : Uri.parse(rootUriString);
    }

    @NonNull
    public String getRootUriString() {
        return rootUriString;
    }

    @NonNull
    public HostingMode getHostingMode() {
        return hostingMode;
    }

    public int getLocalPort() {
        return localPort;
    }

    public boolean isSpaFallbackEnabled() {
        return spaFallbackEnabled;
    }

    @Nullable
    public String getPublicHostname() {
        return publicHostname;
    }

    @NonNull
    public NetworkPolicy getNetworkPolicy() {
        return networkPolicy;
    }

    public boolean isKeepAwakeWhileLocked() {
        return keepAwakeWhileLocked;
    }

    public long getDataWarningThresholdBytes() {
        return dataWarningThresholdBytes;
    }

    public long getDataLimitThresholdBytes() {
        return dataLimitThresholdBytes;
    }

    public static class Builder {
        private String projectId = "";
        private String projectName = "";
        private Uri rootUri;
        private HostingMode hostingMode = HostingMode.LOCAL;
        private int localPort = DEFAULT_LOCAL_PORT;
        private boolean spaFallbackEnabled = true;
        private String publicHostname = null;
        private NetworkPolicy networkPolicy = NetworkPolicy.WIFI_AND_MOBILE;
        private boolean keepAwakeWhileLocked = false;
        private long dataWarningThresholdBytes = DEFAULT_DATA_WARNING_BYTES;
        private long dataLimitThresholdBytes = DEFAULT_DATA_LIMIT_BYTES;

        public Builder() {}

        public Builder setProjectId(@NonNull String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder setProjectName(@NonNull String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder setRootUri(@NonNull Uri rootUri) {
            this.rootUri = rootUri;
            return this;
        }

        public Builder setHostingMode(@NonNull HostingMode hostingMode) {
            this.hostingMode = hostingMode;
            return this;
        }

        public Builder setLocalPort(int localPort) {
            this.localPort = localPort;
            return this;
        }

        public Builder setSpaFallbackEnabled(boolean enabled) {
            this.spaFallbackEnabled = enabled;
            return this;
        }

        public Builder setPublicHostname(@Nullable String hostname) {
            this.publicHostname = hostname;
            return this;
        }

        public Builder setNetworkPolicy(@NonNull NetworkPolicy policy) {
            this.networkPolicy = policy;
            return this;
        }

        public Builder setKeepAwakeWhileLocked(boolean keepAwake) {
            this.keepAwakeWhileLocked = keepAwake;
            return this;
        }

        public Builder setDataWarningThresholdBytes(long bytes) {
            this.dataWarningThresholdBytes = bytes;
            return this;
        }

        public Builder setDataLimitThresholdBytes(long bytes) {
            this.dataLimitThresholdBytes = bytes;
            return this;
        }

        @NonNull
        public DeploymentConfig build() {
            if (projectId == null || projectId.trim().isEmpty()) {
                projectId = "proj_" + Long.toHexString(System.currentTimeMillis());
            }
            if (projectName == null || projectName.trim().isEmpty()) {
                projectName = "Website";
            }
            return new DeploymentConfig(this);
        }
    }
}
