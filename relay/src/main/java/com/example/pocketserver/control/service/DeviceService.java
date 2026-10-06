package com.example.pocketserver.control.service;

import com.example.pocketserver.control.model.Device;
import com.example.pocketserver.control.repository.ControlPlaneRepository;
import java.util.List;
import java.util.UUID;

/**
 * Service managing Android device registration and identity in the Control Plane.
 */
public class DeviceService {

    private final ControlPlaneRepository repository;

    public DeviceService(ControlPlaneRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers an Android device for a user. If deviceId is not specified, a new unique id is generated.
     */
    public Device registerDevice(String userId, String deviceId, String deviceName, String model) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }

        String safeDeviceId = (deviceId != null && !deviceId.trim().isEmpty())
                ? deviceId.trim()
                : "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String safeDeviceName = (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName.trim() : "Android Device";
        String safeModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "Generic Android";

        Device existing = repository.findDeviceById(safeDeviceId);
        if (existing != null) {
            existing.setLastSeen(System.currentTimeMillis());
            return existing;
        }

        Device device = new Device(safeDeviceId, userId, safeDeviceName, safeModel, System.currentTimeMillis());
        return repository.saveDevice(device);
    }

    public List<Device> getDevicesForUser(String userId) {
        return repository.findDevicesByUserId(userId);
    }

    public Device getDevice(String deviceId) {
        return repository.findDeviceById(deviceId);
    }
}
