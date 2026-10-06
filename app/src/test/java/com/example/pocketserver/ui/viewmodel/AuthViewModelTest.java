package com.example.pocketserver.ui.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.core.data.FakeSharedPreferences;
import com.example.pocketserver.core.data.api.ControlApiClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for AuthViewModel input validation, guest mode, and session checks.
 */
public class AuthViewModelTest {

    private FakeSharedPreferences fakePrefs;
    private CredentialStore credentialStore;
    private AuthViewModel viewModel;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });

        fakePrefs = new FakeSharedPreferences();
        credentialStore = new CredentialStore(fakePrefs);
        ControlApiClient apiClient = new ControlApiClient("https://api.pocketserver.dev", credentialStore);

        viewModel = new AuthViewModel(new Application(), credentialStore, apiClient);
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void testEmptyLoginValidation() {
        viewModel.login("", "");
        assertNotNull(viewModel.getErrorMessage().getValue());
        assertTrue(viewModel.getErrorMessage().getValue().contains("Please enter both email and password"));
        assertFalse(Boolean.TRUE.equals(viewModel.getAuthSuccess().getValue()));
    }

    @Test
    public void testShortPasswordRegisterValidation() {
        viewModel.register("dev@test.com", "123");
        assertNotNull(viewModel.getErrorMessage().getValue());
        assertTrue(viewModel.getErrorMessage().getValue().contains("at least 6 characters"));
        assertFalse(Boolean.TRUE.equals(viewModel.getAuthSuccess().getValue()));
    }

    @Test
    public void testSkipLocalMode() {
        viewModel.skipLocalMode();
        assertTrue(Boolean.TRUE.equals(viewModel.getAuthSuccess().getValue()));
    }

    @Test
    public void testIsLoggedInCheck() {
        assertFalse(viewModel.isLoggedIn());
        credentialStore.saveSession("token_xyz", "usr_1", "dev@test.com");
        assertTrue(viewModel.isLoggedIn());
    }
}
