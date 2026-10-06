package com.example.pocketserver.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketserver.R;
import com.example.pocketserver.databinding.FragmentDashboardBinding;
import com.example.pocketserver.engine.model.Deployment;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import com.example.pocketserver.engine.model.HostingMode;
import com.example.pocketserver.ui.activity.NewDeploymentActivity;
import com.example.pocketserver.ui.adapter.RequestLogAdapter;
import com.example.pocketserver.ui.qr.QrCodeBottomSheetDialogFragment;
import com.example.pocketserver.ui.viewmodel.MainViewModel;
import java.util.Locale;

/**
 * Primary Dashboard fragment displaying active deployment state, live URL,
 * real-time telemetry counters, and incoming HTTP request logs.
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private MainViewModel viewModel;
    private RequestLogAdapter logAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupRecyclerView();
        setupActionListeners();
        observeViewModel();
    }

    private void setupRecyclerView() {
        logAdapter = new RequestLogAdapter();
        binding.recyclerRequestLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRequestLogs.setAdapter(logAdapter);
    }

    private void setupActionListeners() {
        binding.btnStopServer.setOnClickListener(v -> viewModel.stopDeployment());
        binding.btnRestartServer.setOnClickListener(v -> viewModel.restartDeployment());

        binding.btnStartFromIdle.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), NewDeploymentActivity.class);
            startActivity(intent);
        });

        binding.btnCopyUrl.setOnClickListener(v -> {
            Deployment deployment = viewModel.getActiveDeployment().getValue();
            if (deployment != null && deployment.getDisplayUrl() != null) {
                ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("PocketServer URL", deployment.getDisplayUrl()));
                    Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnQrCode.setOnClickListener(v -> {
            Deployment deployment = viewModel.getActiveDeployment().getValue();
            if (deployment != null && deployment.getDisplayUrl() != null) {
                QrCodeBottomSheetDialogFragment dialog =
                        QrCodeBottomSheetDialogFragment.newInstance(deployment.getDisplayUrl());
                dialog.show(getChildFragmentManager(), "qr_bottom_sheet");
            }
        });

        binding.btnOpenBrowser.setOnClickListener(v -> {
            Deployment deployment = viewModel.getActiveDeployment().getValue();
            if (deployment != null && deployment.getDisplayUrl() != null) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(deployment.getDisplayUrl()));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "No browser available", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void observeViewModel() {
        viewModel.getDeploymentState().observe(getViewLifecycleOwner(), this::updateStateUI);
        viewModel.getActiveDeployment().observe(getViewLifecycleOwner(), this::updateDeploymentUI);
        viewModel.getTelemetry().observe(getViewLifecycleOwner(), this::updateTelemetryUI);
        viewModel.getRecentRequests().observe(getViewLifecycleOwner(), list -> {
            if (list == null || list.isEmpty()) {
                binding.txtNoRequests.setVisibility(View.VISIBLE);
                logAdapter.submitList(null);
            } else {
                binding.txtNoRequests.setVisibility(View.GONE);
                logAdapter.submitList(list);
            }
        });
    }

    private void updateStateUI(@NonNull DeploymentState state) {
        if (state == DeploymentState.STOPPED && viewModel.getActiveDeployment().getValue() == null) {
            binding.cardActiveDeployment.setVisibility(View.GONE);
            binding.cardIdleState.setVisibility(View.VISIBLE);
            return;
        }

        binding.cardActiveDeployment.setVisibility(View.VISIBLE);
        binding.cardIdleState.setVisibility(View.GONE);

        binding.txtStatusBadge.setText(state.name());
        switch (state) {
            case LIVE:
                binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_live);
                binding.btnStopServer.setEnabled(true);
                binding.btnRestartServer.setEnabled(true);
                break;
            case CONNECTING:
            case RECONNECTING:
            case STARTING:
                binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_connecting);
                binding.btnStopServer.setEnabled(true);
                binding.btnRestartServer.setEnabled(false);
                break;
            case FAILED:
                binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_failed);
                binding.btnStopServer.setEnabled(false);
                binding.btnRestartServer.setEnabled(true);
                break;
            case STOPPED:
            default:
                binding.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_stopped);
                binding.btnStopServer.setEnabled(false);
                binding.btnRestartServer.setEnabled(true);
                break;
        }
    }

    private void updateDeploymentUI(@Nullable Deployment deployment) {
        if (deployment == null) {
            binding.cardActiveDeployment.setVisibility(View.GONE);
            binding.cardIdleState.setVisibility(View.VISIBLE);
            return;
        }

        binding.cardActiveDeployment.setVisibility(View.VISIBLE);
        binding.cardIdleState.setVisibility(View.GONE);

        binding.txtProjectName.setText(deployment.getConfig().getProjectName());

        boolean isPublic = deployment.getConfig().getHostingMode() == HostingMode.PUBLIC;
        binding.txtHostingModeChip.setText(isPublic ? R.string.mode_public : R.string.mode_local);

        String displayUrl = deployment.getDisplayUrl();
        if (displayUrl != null && !displayUrl.isEmpty()) {
            binding.txtDisplayUrl.setText(displayUrl);
            binding.btnCopyUrl.setEnabled(true);
            binding.btnQrCode.setEnabled(true);
            binding.btnOpenBrowser.setEnabled(true);
        } else if (deployment.getState() == DeploymentState.FAILED) {
            String errorMsg = deployment.getErrorMessage() != null ? deployment.getErrorMessage() : "Deployment failed";
            binding.txtDisplayUrl.setText(errorMsg);
            binding.btnCopyUrl.setEnabled(false);
            binding.btnQrCode.setEnabled(false);
            binding.btnOpenBrowser.setEnabled(false);
        } else {
            binding.txtDisplayUrl.setText("Assigning address...");
            binding.btnCopyUrl.setEnabled(false);
            binding.btnQrCode.setEnabled(false);
            binding.btnOpenBrowser.setEnabled(false);
        }
    }

    private void updateTelemetryUI(@NonNull DeploymentTelemetry telemetry) {
        binding.txtMetricRequests.setText(String.valueOf(telemetry.getRequestCount()));
        binding.txtMetricTraffic.setText(formatBytes(telemetry.getBytesTransferred()));
        binding.txtMetricStreams.setText(String.valueOf(telemetry.getActiveStreams()));

        long startedAt = telemetry.getStartedAtTimestamp();
        if (startedAt > 0) {
            long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
            long minutes = elapsedSeconds / 60;
            long seconds = elapsedSeconds % 60;
            if (minutes >= 60) {
                long hours = minutes / 60;
                minutes = minutes % 60;
                binding.txtMetricUptime.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                binding.txtMetricUptime.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
            }
        } else {
            binding.txtMetricUptime.setText("00:00");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
