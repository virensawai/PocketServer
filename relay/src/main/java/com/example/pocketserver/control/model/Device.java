package com.example.pocketserver.control.model;

/**
 * Model representing a registered Android device owned by a user.
 */
public class Device {

    private final String id;
    private final String userId;
    private final String deviceName;
    private final String model;
    private volatile long lastSeen;

    public Device(String id, String userId, String deviceName, String model, long lastSeen) {
        this.id = id;
        this.userId = userId;
        this.deviceName = deviceName;
        this.model = model;
        this.lastSeen = lastSeen;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getModel() {
        return model;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
