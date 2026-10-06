package com.example.pocketserver.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketserver.R;
import com.example.pocketserver.core.data.entity.DeploymentEntity;
import com.example.pocketserver.databinding.ItemDeploymentCardBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RecyclerView adapter for past project deployments history.
 */
public class DeploymentsAdapter extends ListAdapter<DeploymentEntity, DeploymentsAdapter.DeploymentViewHolder> {

    public interface OnDeploymentActionListener {
        void onRedeploy(@NonNull DeploymentEntity deployment);
        void onDelete(@NonNull DeploymentEntity deployment);
    }

    private final OnDeploymentActionListener actionListener;

    public DeploymentsAdapter(@NonNull OnDeploymentActionListener actionListener) {
        super(new DiffUtil.ItemCallback<DeploymentEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull DeploymentEntity oldItem, @NonNull DeploymentEntity newItem) {
                return oldItem.getDeploymentId().equals(newItem.getDeploymentId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull DeploymentEntity oldItem, @NonNull DeploymentEntity newItem) {
                return oldItem.getStatus().equals(newItem.getStatus())
                        && oldItem.getRequestCount() == newItem.getRequestCount()
                        && oldItem.getDataTransferredBytes() == newItem.getDataTransferredBytes();
            }
        });
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public DeploymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeploymentCardBinding binding = ItemDeploymentCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DeploymentViewHolder(binding, actionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DeploymentViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class DeploymentViewHolder extends RecyclerView.ViewHolder {
        private final ItemDeploymentCardBinding binding;
        private final OnDeploymentActionListener listener;

        public DeploymentViewHolder(
                @NonNull ItemDeploymentCardBinding binding,
                @NonNull OnDeploymentActionListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        public void bind(@NonNull DeploymentEntity item) {
            binding.txtItemProjectName.setText(item.getProjectId());
            binding.txtItemMode.setText("PUBLIC".equalsIgnoreCase(item.getHostingMode()) ? "Public WSS" : "Local Wi-Fi");

            String status = item.getStatus();
            binding.txtItemStatus.setText(status);

            if ("LIVE".equalsIgnoreCase(status)) {
                binding.txtItemStatus.setBackgroundResource(R.drawable.bg_status_live);
            } else if ("CONNECTING".equalsIgnoreCase(status) || "RECONNECTING".equalsIgnoreCase(status)) {
                binding.txtItemStatus.setBackgroundResource(R.drawable.bg_status_connecting);
            } else if ("FAILED".equalsIgnoreCase(status)) {
                binding.txtItemStatus.setBackgroundResource(R.drawable.bg_status_failed);
            } else {
                binding.txtItemStatus.setBackgroundResource(R.drawable.bg_status_stopped);
            }

            String dateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(item.getCreatedAt()));
            String bytesStr = formatBytes(item.getDataTransferredBytes());
            String stats = item.getRequestCount() + " requests • " + bytesStr + " transferred • " + dateStr;
            binding.txtItemStats.setText(stats);

            String hostname = item.getPublicHostname();
            if (hostname != null && !hostname.isEmpty()) {
                binding.txtItemHostname.setText("https://" + hostname);
                binding.txtItemHostname.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.txtItemHostname.setVisibility(android.view.View.GONE);
            }

            binding.btnItemRedeploy.setOnClickListener(v -> listener.onRedeploy(item));
            binding.btnItemDelete.setOnClickListener(v -> listener.onDelete(item));
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
    }
}
