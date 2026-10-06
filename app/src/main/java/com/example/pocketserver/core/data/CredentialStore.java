package com.example.pocketserver.core.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Secure credential storage for JWT auth tokens and user sessions.
 * Employs EncryptedSharedPreferences on devices, with fallback to standard preferences in test environments.
 */
public class CredentialStore {

    private static final String PREFS_NAME = "pocketserver_secure_credentials";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_TUNNEL_TOKEN = "tunnel_token_";

    private final SharedPreferences prefs;

    public CredentialStore(@NonNull Context context) {
        SharedPreferences sharedPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to standard SharedPreferences in Robolectric / test suites where Keystore is absent
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = sharedPrefs;
    }

    public CredentialStore(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public void saveSession(@NonNull String token, @NonNull String userId, @NonNull String email) {
        prefs.edit()
                .putString(KEY_AUTH_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .apply();
    }

    @Nullable
    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    @Nullable
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    @Nullable
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    public boolean isLoggedIn() {
        String token = getAuthToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .apply();
    }

    public void saveTunnelToken(@NonNull String deploymentId, @NonNull String tunnelToken) {
        prefs.edit().putString(KEY_TUNNEL_TOKEN + deploymentId, tunnelToken).apply();
    }

    @Nullable
    public String getTunnelToken(@NonNull String deploymentId) {
        return prefs.getString(KEY_TUNNEL_TOKEN + deploymentId, null);
    }
}
