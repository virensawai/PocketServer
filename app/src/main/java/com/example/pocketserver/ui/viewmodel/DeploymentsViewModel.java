package com.example.pocketserver.ui.viewmodel;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.pocketserver.core.data.PocketServerDatabase;
import com.example.pocketserver.core.data.entity.DeploymentEntity;
import com.example.pocketserver.core.data.entity.ProjectEntity;
import com.example.pocketserver.engine.model.DeploymentConfig;
import com.example.pocketserver.engine.model.HostingMode;
import com.example.pocketserver.engine.service.DeploymentManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel managing persisted projects and past deployments history.
 */
public class DeploymentsViewModel extends AndroidViewModel {

    private final PocketServerDatabase database;
    private final DeploymentManager deploymentManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<DeploymentEntity>> deployments = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public DeploymentsViewModel(@NonNull Application application) {
        super(application);
        this.database = PocketServerDatabase.getInstance(application);
        this.deploymentManager = DeploymentManager.getInstance(application);
    }

    public DeploymentsViewModel(
            @NonNull Application application,
            @NonNull PocketServerDatabase database,
            @NonNull DeploymentManager deploymentManager) {
        super(application);
        this.database = database;
        this.deploymentManager = deploymentManager;
    }

    @NonNull
    public LiveData<List<DeploymentEntity>> getDeployments() {
        return deployments;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadDeployments() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                List<DeploymentEntity> list = database.deploymentDao().getAllDeployments();
                deployments.postValue(list);
            } catch (Exception e) {
                errorMessage.postValue("Failed to load deployments: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void deleteDeployment(@NonNull DeploymentEntity entity) {
        executor.execute(() -> {
            try {
                database.deploymentDao().delete(entity);
                loadDeployments();
            } catch (Exception e) {
                errorMessage.postValue("Failed to delete deployment: " + e.getMessage());
            }
        });
    }

    public void redeploy(@NonNull DeploymentEntity entity) {
        executor.execute(() -> {
            try {
                ProjectEntity project = database.projectDao().getProjectById(entity.getProjectId());
                if (project == null || project.getRootUriString() == null) {
                    errorMessage.postValue("Project files reference not found for " + entity.getProjectId());
                    return;
                }

                HostingMode mode = "PUBLIC".equalsIgnoreCase(entity.getHostingMode())
                        ? HostingMode.PUBLIC : HostingMode.LOCAL;

                DeploymentConfig config = new DeploymentConfig.Builder()
                        .setProjectId(project.getProjectId())
                        .setProjectName(project.getName())
                        .setRootUri(Uri.parse(project.getRootUriString()))
                        .setHostingMode(mode)
                        .setPublicHostname(entity.getPublicHostname())
                        .setSpaFallbackEnabled(entity.isSpaFallbackEnabled())
                        .build();

                deploymentManager.startDeployment(config);
            } catch (Exception e) {
                errorMessage.postValue("Redeployment failed: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
