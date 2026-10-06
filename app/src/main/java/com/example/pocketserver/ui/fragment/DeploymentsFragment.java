package com.example.pocketserver.ui.fragment;

import android.content.Intent;
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
import com.example.pocketserver.MainActivity;
import com.example.pocketserver.R;
import com.example.pocketserver.core.data.entity.DeploymentEntity;
import com.example.pocketserver.databinding.FragmentDeploymentsBinding;
import com.example.pocketserver.ui.activity.NewDeploymentActivity;
import com.example.pocketserver.ui.adapter.DeploymentsAdapter;
import com.example.pocketserver.ui.viewmodel.DeploymentsViewModel;

/**
 * Fragment presenting past project deployments with one-tap redeploy and delete actions.
 */
public class DeploymentsFragment extends Fragment implements DeploymentsAdapter.OnDeploymentActionListener {

    private FragmentDeploymentsBinding binding;
    private DeploymentsViewModel viewModel;
    private DeploymentsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDeploymentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DeploymentsViewModel.class);

        adapter = new DeploymentsAdapter(this);
        binding.recyclerDeployments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDeployments.setAdapter(adapter);

        binding.btnEmptyNewDeployment.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), NewDeploymentActivity.class);
            startActivity(intent);
        });

        viewModel.getDeployments().observe(getViewLifecycleOwner(), list -> {
            if (list == null || list.isEmpty()) {
                binding.recyclerDeployments.setVisibility(View.GONE);
                binding.layoutEmptyDeployments.setVisibility(View.VISIBLE);
            } else {
                binding.recyclerDeployments.setVisibility(View.VISIBLE);
                binding.layoutEmptyDeployments.setVisibility(View.GONE);
                adapter.submitList(list);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadDeployments();
    }

    @Override
    public void onRedeploy(@NonNull DeploymentEntity deployment) {
        viewModel.redeploy(deployment);
        Toast.makeText(requireContext(), "Starting " + deployment.getProjectId(), Toast.LENGTH_SHORT).show();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToDashboard();
        }
    }

    @Override
    public void onDelete(@NonNull DeploymentEntity deployment) {
        viewModel.deleteDeployment(deployment);
        Toast.makeText(requireContext(), "Deployment removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
