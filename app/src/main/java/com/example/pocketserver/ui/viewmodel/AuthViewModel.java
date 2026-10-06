package com.example.pocketserver.ui.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.core.data.api.ControlApiClient;
import com.google.gson.JsonObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel managing user authentication and device registration.
 */
public class AuthViewModel extends AndroidViewModel {

    private final CredentialStore credentialStore;
    private final ControlApiClient apiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> authSuccess = new MutableLiveData<>(false);

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.credentialStore = new CredentialStore(application);
        this.apiClient = new ControlApiClient(LimitsConfig.DEFAULT_API_BASE_URL, credentialStore);
    }

    public AuthViewModel(
            @NonNull Application application,
            @NonNull CredentialStore credentialStore,
            @NonNull ControlApiClient apiClient) {
        super(application);
        this.credentialStore = credentialStore;
        this.apiClient = apiClient;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<Boolean> getAuthSuccess() {
        return authSuccess;
    }

    public boolean isLoggedIn() {
        return credentialStore.isLoggedIn();
    }

    public void login(@NonNull String email, @NonNull String password) {
        if (email.trim().isEmpty() || password.trim().isEmpty()) {
            errorMessage.setValue("Please enter both email and password.");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                JsonObject response = apiClient.login(email.trim(), password);
                if (response.has("token")) {
                    String token = response.get("token").getAsString();
                    String userId = response.has("userId") ? response.get("userId").getAsString() : "user_" + email.hashCode();
                    credentialStore.saveSession(token, userId, email);
                    authSuccess.postValue(true);
                } else if (response.has("error")) {
                    errorMessage.postValue(response.get("error").getAsString());
                } else {
                    errorMessage.postValue("Login failed. Please check credentials.");
                }
            } catch (Exception e) {
                // If offline or network error, let user proceed or display clear message
                errorMessage.postValue("Connection failed: " + e.getMessage() + ". You can use Local Mode.");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void register(@NonNull String email, @NonNull String password) {
        if (email.trim().isEmpty() || password.trim().isEmpty()) {
            errorMessage.setValue("Please enter both email and password.");
            return;
        }
        if (password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters.");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                JsonObject regResponse = apiClient.register(email.trim(), password);
                if (regResponse.has("error")) {
                    errorMessage.postValue(regResponse.get("error").getAsString());
                    return;
                }
                // Automatically login upon registration
                JsonObject loginResponse = apiClient.login(email.trim(), password);
                if (loginResponse.has("token")) {
                    String token = loginResponse.get("token").getAsString();
                    String userId = loginResponse.has("userId") ? loginResponse.get("userId").getAsString() : "user_" + email.hashCode();
                    credentialStore.saveSession(token, userId, email);
                    authSuccess.postValue(true);
                } else {
                    authSuccess.postValue(true);
                }
            } catch (Exception e) {
                errorMessage.postValue("Registration error: " + e.getMessage() + ". You can continue in Local Mode.");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void skipLocalMode() {
        authSuccess.setValue(true);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
