package com.example.pocketserver.core.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * Manages unique, persistent device identity for Android client registration in the Control Plane.
 */
public class DeviceIdentityManager {

    private static final String PREFS_NAME = "pocketserver_device_identity";
    private static final String KEY_DEVICE_ID = "device_id";

    private final SharedPreferences prefs;

    public DeviceIdentityManager(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public DeviceIdentityManager(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Retrieves the persistent deviceId, or generates and saves a new unique one.
     */
    @NonNull
    public synchronized String getDeviceId() {
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    @NonNull
    public String getDeviceName() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "Android";
        String model = Build.MODEL != null ? Build.MODEL : "Device";
        return manufacturer + " " + model;
    }

    @NonNull
    public String getModel() {
        return Build.MODEL != null ? Build.MODEL : "Generic";
    }
}
