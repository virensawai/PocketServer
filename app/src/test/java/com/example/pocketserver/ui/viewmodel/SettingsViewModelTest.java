package com.example.pocketserver.ui.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.core.data.FakeSharedPreferences;
import com.example.pocketserver.engine.model.NetworkPolicy;
import com.example.pocketserver.engine.service.DeploymentManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for SettingsViewModel.
 */
public class SettingsViewModelTest {

    private FakeSharedPreferences fakePrefs;
    private CredentialStore credentialStore;

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
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void testSessionManagement() {
        credentialStore.saveSession("token_123", "usr_456", "tester@pocketserver.dev");

        SettingsViewModel viewModel = new SettingsViewModel(
                new Application(),
                DeploymentManager.getInstance(new Application()),
                credentialStore
        );

        assertTrue(viewModel.isLoggedIn());
        assertEquals("tester@pocketserver.dev", viewModel.getUserEmail());

        viewModel.signOut();
        assertFalse(viewModel.isLoggedIn());
    }
}
