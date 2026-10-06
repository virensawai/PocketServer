package com.example.pocketserver.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for NetworkPolicy configurations.
 */
public class NetworkPolicyTest {

    @Test
    public void testPolicyDisplayNames() {
        assertEquals("Wi-Fi Only", NetworkPolicy.WIFI_ONLY.getDisplayName());
        assertEquals("Wi-Fi + Mobile Data", NetworkPolicy.WIFI_AND_MOBILE.getDisplayName());
    }

    @Test
    public void testPolicySatisfiedLogic() {
        // Under WIFI_ONLY:
        // Wi-Fi connected: satisfied
        // Cellular only connected: not satisfied
        boolean wifiConnected = true;
        boolean cellularConnected = false;

        assertTrue(isPolicySatisfied(NetworkPolicy.WIFI_ONLY, wifiConnected, cellularConnected));
        assertFalse(isPolicySatisfied(NetworkPolicy.WIFI_ONLY, false, true));

        // Under WIFI_AND_MOBILE:
        // Wi-Fi or Cellular are both satisfied
        assertTrue(isPolicySatisfied(NetworkPolicy.WIFI_AND_MOBILE, true, false));
        assertTrue(isPolicySatisfied(NetworkPolicy.WIFI_AND_MOBILE, false, true));
        assertFalse(isPolicySatisfied(NetworkPolicy.WIFI_AND_MOBILE, false, false));
    }

    private boolean isPolicySatisfied(NetworkPolicy policy, boolean isWifi, boolean isCellular) {
        boolean isConnected = isWifi || isCellular;
        if (!isConnected) return false;
        if (policy == NetworkPolicy.WIFI_ONLY) {
            return isWifi;
        }
        return true;
    }
}
