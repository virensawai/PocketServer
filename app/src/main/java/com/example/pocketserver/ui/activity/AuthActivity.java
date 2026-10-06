package com.example.pocketserver.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.pocketserver.MainActivity;
import com.example.pocketserver.R;
import com.example.pocketserver.databinding.ActivityAuthBinding;
import com.example.pocketserver.ui.viewmodel.AuthViewModel;
import com.google.android.material.tabs.TabLayout;

/**
 * Authentication activity offering sign-in, registration, and guest local-only mode.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupTabs();
        setupListeners();
        observeViewModel();
    }

    private void setupTabs() {
        binding.authTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.btnSubmitAuth.setText(R.string.action_sign_in);
                } else {
                    binding.btnSubmitAuth.setText(R.string.action_register);
                }
                binding.cardError.setVisibility(View.GONE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupListeners() {
        binding.btnSubmitAuth.setOnClickListener(v -> {
            String email = binding.editEmail.getText() != null ? binding.editEmail.getText().toString() : "";
            String password = binding.editPassword.getText() != null ? binding.editPassword.getText().toString() : "";

            int selectedTab = binding.authTabs.getSelectedTabPosition();
            if (selectedTab == 0) {
                viewModel.login(email, password);
            } else {
                viewModel.register(email, password);
            }
        });

        binding.btnSkipLocal.setOnClickListener(v -> {
            viewModel.skipLocalMode();
        });
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            binding.authProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnSubmitAuth.setEnabled(!loading);
            binding.btnSkipLocal.setEnabled(!loading);
            binding.editEmail.setEnabled(!loading);
            binding.editPassword.setEnabled(!loading);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                binding.txtErrorMessage.setText(error);
                binding.cardError.setVisibility(View.VISIBLE);
            } else {
                binding.cardError.setVisibility(View.GONE);
            }
        });

        viewModel.getAuthSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
