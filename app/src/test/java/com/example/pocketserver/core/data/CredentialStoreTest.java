package com.example.pocketserver.core.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CredentialStoreTest {

    private CredentialStore credentialStore;

    @Before
    public void setUp() {
        FakeSharedPreferences fakePrefs = new FakeSharedPreferences();
        credentialStore = new CredentialStore(fakePrefs);
    }

    @Test
    public void testSaveAndRetrieveSession() {
        assertFalse(credentialStore.isLoggedIn());
        assertNull(credentialStore.getAuthToken());

        credentialStore.saveSession("pst_token_abc", "usr_123", "user@test.com");

        assertTrue(credentialStore.isLoggedIn());
        assertEquals("pst_token_abc", credentialStore.getAuthToken());
        assertEquals("usr_123", credentialStore.getUserId());
        assertEquals("user@test.com", credentialStore.getUserEmail());

        credentialStore.clearSession();
        assertFalse(credentialStore.isLoggedIn());
        assertNull(credentialStore.getAuthToken());
    }

    @Test
    public void testSaveAndRetrieveTunnelToken() {
        assertNull(credentialStore.getTunnelToken("dep_100"));

        credentialStore.saveTunnelToken("dep_100", "ttok_xyz");
        assertEquals("ttok_xyz", credentialStore.getTunnelToken("dep_100"));
    }
}
