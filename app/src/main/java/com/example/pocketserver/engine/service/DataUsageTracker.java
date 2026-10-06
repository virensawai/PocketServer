package com.example.pocketserver.engine.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.engine.model.DeploymentConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe monitor tracking data transferred across HTTP and tunnel connections.
 * Enforces configurable warning thresholds and hard limits to prevent cellular data exhaustion.
 */
public class DataUsageTracker {

    public interface DataUsageListener {
        /**
         * Invoked when cumulative data reaches the configured warning threshold.
         */
        void onWarningThresholdReached(long bytesTransferred, long warningThreshold);

        /**
         * Invoked when cumulative data exceeds the configured hard cap.
         */
        void onLimitThresholdExceeded(long bytesTransferred, long limitThreshold);
    }

    private final long warningThresholdBytes;
    private final long limitThresholdBytes;
    private final AtomicLong bytesTransferred = new AtomicLong(0);
    private final AtomicBoolean warningFired = new AtomicBoolean(false);
    private final AtomicBoolean limitFired = new AtomicBoolean(false);
    private volatile DataUsageListener listener;

    public DataUsageTracker() {
        this(DeploymentConfig.DEFAULT_DATA_WARNING_BYTES, DeploymentConfig.DEFAULT_DATA_LIMIT_BYTES);
    }

    public DataUsageTracker(long warningThresholdBytes, long limitThresholdBytes) {
        this.warningThresholdBytes = warningThresholdBytes;
        this.limitThresholdBytes = limitThresholdBytes;
    }

    public void setListener(@Nullable DataUsageListener listener) {
        this.listener = listener;
    }

    /**
     * Records additional transferred bytes and checks against guardrail thresholds.
     *
     * @param bytes additional bytes transferred
     * @return true if limit threshold was exceeded by this or a prior addition
     */
    public boolean addBytes(long bytes) {
        if (bytes <= 0) {
            return limitFired.get();
        }

        long currentTotal = bytesTransferred.addAndGet(bytes);

        // Check warning threshold
        if (currentTotal >= warningThresholdBytes && warningFired.compareAndSet(false, true)) {
            DataUsageListener l = this.listener;
            if (l != null) {
                l.onWarningThresholdReached(currentTotal, warningThresholdBytes);
            }
        }

        // Check hard cap limit
        if (currentTotal >= limitThresholdBytes && limitFired.compareAndSet(false, true)) {
            DataUsageListener l = this.listener;
            if (l != null) {
                l.onLimitThresholdExceeded(currentTotal, limitThresholdBytes);
            }
        }

        return limitFired.get();
    }

    public long getBytesTransferred() {
        return bytesTransferred.get();
    }

    public long getWarningThresholdBytes() {
        return warningThresholdBytes;
    }

    public long getLimitThresholdBytes() {
        return limitThresholdBytes;
    }

    public boolean isWarningFired() {
        return warningFired.get();
    }

    public boolean isLimitFired() {
        return limitFired.get();
    }

    public void reset() {
        bytesTransferred.set(0);
        warningFired.set(false);
        limitFired.set(false);
    }
}
