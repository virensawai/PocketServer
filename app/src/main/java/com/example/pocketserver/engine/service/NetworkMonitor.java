package com.example.pocketserver.engine.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.engine.model.NetworkPolicy;

/**
 * Monitors active device network connectivity using standard ConnectivityManager callbacks.
 * Dispatches availability, disconnect, and policy violation events based on active NetworkPolicy.
 */
public class NetworkMonitor {

    public interface NetworkStateListener {
        void onNetworkAvailable(boolean isWifi, boolean isCellular, boolean isMetered);
        void onNetworkLost();
        void onPolicyViolation(@NonNull NetworkPolicy policy);
    }

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private volatile NetworkPolicy policy = NetworkPolicy.WIFI_AND_MOBILE;
    private volatile NetworkStateListener listener;
    private volatile boolean isMonitoring = false;

    private volatile boolean isConnected = false;
    private volatile boolean isWifi = false;
    private volatile boolean isCellular = false;
    private volatile boolean isMetered = false;

    private ConnectivityManager.NetworkCallback networkCallback;

    public NetworkMonitor(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void setPolicy(@NonNull NetworkPolicy policy) {
        this.policy = policy;
        evaluatePolicy();
    }

    @NonNull
    public NetworkPolicy getPolicy() {
        return policy;
    }

    public void setListener(@Nullable NetworkStateListener listener) {
        this.listener = listener;
    }

    public synchronized void startMonitoring() {
        if (isMonitoring || connectivityManager == null) {
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateNetworkCapabilities(network);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                applyCapabilities(capabilities);
            }

            @Override
            public void onLost(@NonNull Network network) {
                handleNetworkLost();
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            isMonitoring = true;
            // Immediate check on start
            Network active = connectivityManager.getActiveNetwork();
            if (active != null) {
                updateNetworkCapabilities(active);
            } else {
                handleNetworkLost();
            }
        } catch (Exception e) {
            // In testing environments or restricted profiles
            isMonitoring = false;
        }
    }

    public synchronized void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        networkCallback = null;
        isMonitoring = false;
    }

    private void updateNetworkCapabilities(@NonNull Network network) {
        if (connectivityManager == null) return;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities != null) {
            applyCapabilities(capabilities);
        } else {
            handleNetworkLost();
        }
    }

    /**
     * Internal update method exposed for unit testing or direct capabilities injection.
     */
    public synchronized void applyCapabilities(@NonNull NetworkCapabilities capabilities) {
        isConnected = true;
        isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

        if (listener != null) {
            listener.onNetworkAvailable(isWifi, isCellular, isMetered);
        }

        evaluatePolicy();
    }

    public synchronized void handleNetworkLost() {
        isConnected = false;
        isWifi = false;
        isCellular = false;
        isMetered = false;

        if (listener != null) {
            listener.onNetworkLost();
        }
    }

    private synchronized void evaluatePolicy() {
        if (!isConnected) {
            return;
        }

        if (policy == NetworkPolicy.WIFI_ONLY && !isWifi) {
            if (listener != null) {
                listener.onPolicyViolation(policy);
            }
        }
    }

    public boolean isPolicySatisfied() {
        if (!isConnected) {
            return false;
        }
        if (policy == NetworkPolicy.WIFI_ONLY) {
            return isWifi;
        }
        return true;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isWifi() {
        return isWifi;
    }

    public boolean isCellular() {
        return isCellular;
    }

    public boolean isMetered() {
        return isMetered;
    }
}
