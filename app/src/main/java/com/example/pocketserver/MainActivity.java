package com.example.pocketserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.pocketserver.databinding.ActivityMainBinding;
import com.example.pocketserver.ui.activity.NewDeploymentActivity;
import com.example.pocketserver.ui.fragment.DashboardFragment;
import com.example.pocketserver.ui.fragment.DeploymentsFragment;
import com.example.pocketserver.ui.fragment.SettingsFragment;

/**
 * Main application host activity.
 * Coordinates bottom navigation between Dashboard, Deployments, and Settings fragments.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final DashboardFragment dashboardFragment = new DashboardFragment();
    private final DeploymentsFragment deploymentsFragment = new DeploymentsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Runtime permission handled; foreground service notification enabled
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.topToolbar);

        checkNotificationPermission();
        setupBottomNavigation();
        setupFab();

        if (savedInstanceState == null) {
            switchFragment(dashboardFragment, getString(R.string.nav_dashboard));
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                switchFragment(dashboardFragment, getString(R.string.nav_dashboard));
                binding.fabDeploy.show();
                return true;
            } else if (itemId == R.id.nav_deployments) {
                switchFragment(deploymentsFragment, getString(R.string.nav_deployments));
                binding.fabDeploy.show();
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchFragment(settingsFragment, getString(R.string.nav_settings));
                binding.fabDeploy.hide();
                return true;
            }
            return false;
        });
    }

    private void setupFab() {
        binding.fabDeploy.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NewDeploymentActivity.class);
            startActivity(intent);
        });
    }

    private void switchFragment(@NonNull Fragment fragment, @NonNull String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    public void navigateToDashboard() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
    }

    public ActivityMainBinding getBinding() {
        return binding;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
