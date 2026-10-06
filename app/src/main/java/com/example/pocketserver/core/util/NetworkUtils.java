package com.example.pocketserver.core.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * Utility helpers for resolving network interfaces, IP addresses, and connectivity states.
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Resolves the primary local IPv4 address of the device (e.g. 192.168.1.15).
     * Scans active network interfaces, prioritizing Wi-Fi (wlan) or Ethernet (eth).
     *
     * @return IPv4 string or "127.0.0.1" if no external interface is found
     */
    @NonNull
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            // First pass: look specifically for wlan or eth interfaces
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) {
                    continue;
                }
                String name = intf.getName().toLowerCase();
                if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                    String ip = extractIpv4(intf);
                    if (ip != null) {
                        return ip;
                    }
                }
            }

            // Second pass: any active non-loopback interface
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) {
                    continue;
                }
                String ip = extractIpv4(intf);
                if (ip != null) {
                    return ip;
                }
            }
        } catch (Exception ignored) {
            // Fallback to loopback on failure
        }
        return "127.0.0.1";
    }

    @Nullable
    private static String extractIpv4(NetworkInterface intf) {
        for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
            if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                String hostAddress = addr.getHostAddress();
                if (hostAddress != null && !hostAddress.isEmpty() && !hostAddress.startsWith("127.")) {
                    return hostAddress;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the device currently has an active network connection.
     */
    public static boolean isConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        android.net.Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }
}
