package com.example.pocketserver.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.pocketserver.R;
import com.example.pocketserver.core.storage.ProjectValidator;
import com.example.pocketserver.databinding.ActivityNewDeploymentBinding;
import com.example.pocketserver.engine.model.HostingMode;
import com.example.pocketserver.ui.viewmodel.NewDeploymentViewModel;

/**
 * Activity for configuring and launching a new website deployment from a SAF folder.
 */
public class NewDeploymentActivity extends AppCompatActivity {

    private ActivityNewDeploymentBinding binding;
    private NewDeploymentViewModel viewModel;

    private final ActivityResultLauncher<Uri> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    try {
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    } catch (SecurityException ignored) {
                        // Persistable permission might already be held or unsupported in tests
                    }
                    binding.txtSelectedPath.setText(uri.toString());
                    viewModel.onFolderSelected(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewDeploymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NewDeploymentViewModel.class);

        setupToolbar();
        setupHostingModeRadio();
        setupListeners();
        observeViewModel();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.newDeploymentToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.newDeploymentToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupHostingModeRadio() {
        binding.radioGroupHostingMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPublic = (checkedId == R.id.radioPublic);
            binding.inputLayoutSubdomain.setEnabled(isPublic);
            binding.inputLayoutSubdomain.setAlpha(isPublic ? 1.0f : 0.5f);
        });
    }

    private void setupListeners() {
        binding.btnSelectFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));

        binding.btnDeployNow.setOnClickListener(v -> {
            String projectName = binding.editProjectName.getText() != null
                    ? binding.editProjectName.getText().toString() : "";
            HostingMode mode = binding.radioPublic.isChecked()
                    ? HostingMode.PUBLIC : HostingMode.LOCAL;
            String subdomain = binding.editSubdomain.getText() != null
                    ? binding.editSubdomain.getText().toString() : null;

            int port = 8080;
            if (binding.editPort.getText() != null && !binding.editPort.getText().toString().isEmpty()) {
                try {
                    port = Integer.parseInt(binding.editPort.getText().toString());
                } catch (NumberFormatException ignored) {}
            }

            boolean spaFallback = binding.switchSpaFallback.isChecked();
            boolean keepAwake = binding.switchKeepAwake.isChecked();

            viewModel.deploy(projectName, mode, subdomain, port, spaFallback, keepAwake);
        });
    }

    private void observeViewModel() {
        viewModel.getIsValidating().observe(this, validating -> {
            binding.btnSelectFolder.setEnabled(!validating);
            if (validating) {
                binding.layoutValidationBanner.setVisibility(View.VISIBLE);
                binding.imgValidationIcon.setImageResource(R.drawable.ic_refresh);
                binding.imgValidationIcon.setColorFilter(ContextCompat.getColor(this, R.color.pocket_primary));
                binding.txtValidationMessage.setText("Validating project files...");
                binding.txtValidationMessage.setTextColor(ContextCompat.getColor(this, R.color.pocket_primary));
                binding.btnDeployNow.setEnabled(false);
            }
        });

        viewModel.getValidationResult().observe(this, result -> {
            if (result == null) {
                binding.layoutValidationBanner.setVisibility(View.GONE);
                binding.btnDeployNow.setEnabled(false);
                return;
            }

            binding.layoutValidationBanner.setVisibility(View.VISIBLE);
            if (result.isValid()) {
                binding.imgValidationIcon.setImageResource(R.drawable.ic_check_circle);
                binding.imgValidationIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_live));
                binding.txtValidationMessage.setText(
                        getString(R.string.validation_success, result.getImmediateItemCount()));
                binding.txtValidationMessage.setTextColor(ContextCompat.getColor(this, R.color.status_live));

                if (binding.editProjectName.getText() == null || binding.editProjectName.getText().toString().trim().isEmpty()) {
                    binding.editProjectName.setText(result.getProjectName());
                }
                binding.btnDeployNow.setEnabled(true);
            } else {
                binding.imgValidationIcon.setImageResource(R.drawable.ic_error);
                binding.imgValidationIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_failed));
                binding.txtValidationMessage.setText(result.getErrorMessage() != null
                        ? result.getErrorMessage() : getString(R.string.validation_error_no_index));
                binding.txtValidationMessage.setTextColor(ContextCompat.getColor(this, R.color.status_failed));
                binding.btnDeployNow.setEnabled(false);
            }
        });

        viewModel.getIsDeploying().observe(this, deploying -> {
            binding.btnDeployNow.setEnabled(!deploying);
            binding.btnDeployNow.setText(deploying ? "Deploying..." : getString(R.string.btn_deploy_now));
        });

        viewModel.getDeploymentFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Toast.makeText(this, "Deployment started successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
