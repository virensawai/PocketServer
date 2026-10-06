package com.example.pocketserver.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.pocketserver.R;
import com.example.pocketserver.core.config.LimitsConfig;
import com.example.pocketserver.databinding.FragmentSettingsBinding;
import com.example.pocketserver.engine.model.NetworkPolicy;
import com.example.pocketserver.ui.activity.AuthActivity;
import com.example.pocketserver.ui.viewmodel.SettingsViewModel;

/**
 * Settings fragment managing network execution policies, power locks, and user sessions.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        binding.txtRelayHost.setText(LimitsConfig.DEFAULT_RELAY_URL);
        binding.txtApiEndpoint.setText(LimitsConfig.DEFAULT_API_BASE_URL);

        setupNetworkPolicyControls();
        setupKeepAwakeControl();
        setupBatteryOptimizationControls();
        setupAccountControls();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBatteryOptimizationStatus();
    }

    private void setupBatteryOptimizationControls() {
        updateBatteryOptimizationStatus();
        binding.cardBatteryGuidance.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
    }

    private void updateBatteryOptimizationStatus() {
        if (binding == null || getContext() == null) return;
        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
            binding.txtBatteryDesc.setText(R.string.battery_unrestricted);
        } else {
            binding.txtBatteryDesc.setText(R.string.battery_restricted);
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(requireContext(), "Open system settings to disable battery optimization", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupNetworkPolicyControls() {
        viewModel.getNetworkPolicy().observe(getViewLifecycleOwner(), policy -> {
            if (policy == NetworkPolicy.WIFI_ONLY) {
                binding.radioWifiOnly.setChecked(true);
            } else {
                binding.radioWifiAndMobile.setChecked(true);
            }
        });

        binding.radioGroupNetworkPolicy.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioWifiOnly) {
                viewModel.setNetworkPolicy(NetworkPolicy.WIFI_ONLY);
            } else {
                viewModel.setNetworkPolicy(NetworkPolicy.WIFI_AND_MOBILE);
            }
        });
    }

    private void setupKeepAwakeControl() {
        viewModel.getKeepAwakeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.switchSettingsKeepAwake.setOnCheckedChangeListener(null);
            binding.switchSettingsKeepAwake.setChecked(Boolean.TRUE.equals(enabled));
            binding.switchSettingsKeepAwake.setOnCheckedChangeListener((btn, isChecked) ->
                    viewModel.setKeepAwakeEnabled(isChecked));
        });
    }

    private void setupAccountControls() {
        updateAccountStatus();

        binding.btnSignOut.setOnClickListener(v -> {
            if (viewModel.isLoggedIn()) {
                viewModel.signOut();
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show();
                updateAccountStatus();
            } else {
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                startActivity(intent);
            }
        });
    }

    private void updateAccountStatus() {
        if (viewModel.isLoggedIn()) {
            String email = viewModel.getUserEmail();
            binding.txtAccountStatus.setText(getString(R.string.logged_in_as, email != null ? email : "User"));
            binding.btnSignOut.setText(R.string.action_sign_out);
        } else {
            binding.txtAccountStatus.setText(R.string.not_logged_in);
            binding.btnSignOut.setText(R.string.action_sign_in);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
