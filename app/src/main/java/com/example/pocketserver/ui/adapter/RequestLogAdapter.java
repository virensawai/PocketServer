package com.example.pocketserver.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketserver.R;
import com.example.pocketserver.databinding.ItemRequestLogBinding;
import com.example.pocketserver.engine.model.RequestLogItem;

/**
 * RecyclerView adapter for the live streaming HTTP request logs on the Dashboard.
 */
public class RequestLogAdapter extends ListAdapter<RequestLogItem, RequestLogAdapter.LogViewHolder> {

    public RequestLogAdapter() {
        super(new DiffUtil.ItemCallback<RequestLogItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull RequestLogItem oldItem, @NonNull RequestLogItem newItem) {
                return oldItem.getRequestId().equals(newItem.getRequestId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull RequestLogItem oldItem, @NonNull RequestLogItem newItem) {
                return oldItem.getStatusCode() == newItem.getStatusCode()
                        && oldItem.getBytes() == newItem.getBytes()
                        && oldItem.getDurationMs() == newItem.getDurationMs();
            }
        });
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRequestLogBinding binding = ItemRequestLogBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final ItemRequestLogBinding binding;

        public LogViewHolder(@NonNull ItemRequestLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull RequestLogItem item) {
            binding.txtLogMethod.setText(item.getMethod());
            binding.txtLogPath.setText(item.getPath());
            binding.txtLogStatus.setText(String.valueOf(item.getStatusCode()));

            String meta = item.getFormattedBytes() + " • " + item.getDurationMs() + "ms • " + item.getFormattedTime();
            binding.txtLogMeta.setText(meta);

            // Dynamic status color coding
            int code = item.getStatusCode();
            int statusColor;
            if (code >= 200 && code < 300) {
                statusColor = ContextCompat.getColor(itemView.getContext(), R.color.badge_2xx);
            } else if (code >= 300 && code < 400) {
                statusColor = ContextCompat.getColor(itemView.getContext(), R.color.badge_3xx);
            } else if (code >= 400 && code < 500) {
                statusColor = ContextCompat.getColor(itemView.getContext(), R.color.badge_4xx);
            } else {
                statusColor = ContextCompat.getColor(itemView.getContext(), R.color.badge_5xx);
            }
            binding.txtLogStatus.setTextColor(statusColor);
        }
    }
}
