package com.example.pocketserver.ui.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.engine.model.NetworkPolicy;
import com.example.pocketserver.engine.service.DeploymentManager;

/**
 * ViewModel managing server network policies, power locks, and user session settings.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final DeploymentManager deploymentManager;
    private final CredentialStore credentialStore;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        this.deploymentManager = DeploymentManager.getInstance(application);
        this.credentialStore = new CredentialStore(application);
    }

    public SettingsViewModel(
            @NonNull Application application,
            @NonNull DeploymentManager deploymentManager,
            @NonNull CredentialStore credentialStore) {
        super(application);
        this.deploymentManager = deploymentManager;
        this.credentialStore = credentialStore;
    }

    @NonNull
    public LiveData<NetworkPolicy> getNetworkPolicy() {
        return deploymentManager.getNetworkPolicy();
    }

    @NonNull
    public LiveData<Boolean> getKeepAwakeEnabled() {
        return deploymentManager.getKeepAwakeEnabled();
    }

    public void setNetworkPolicy(@NonNull NetworkPolicy policy) {
        deploymentManager.setNetworkPolicy(policy);
    }

    public void setKeepAwakeEnabled(boolean enabled) {
        deploymentManager.setKeepAwakeEnabled(enabled);
    }

    @Nullable
    public String getUserEmail() {
        return credentialStore.getUserEmail();
    }

    public boolean isLoggedIn() {
        return credentialStore.isLoggedIn();
    }

    public void signOut() {
        credentialStore.clearSession();
    }
}
