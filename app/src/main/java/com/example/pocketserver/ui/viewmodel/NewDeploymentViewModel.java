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
import com.example.pocketserver.core.storage.ProjectValidator;
import com.example.pocketserver.engine.model.DeploymentConfig;
import com.example.pocketserver.engine.model.HostingMode;
import com.example.pocketserver.engine.service.DeploymentManager;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel managing project folder selection, background validation,
 * and deployment initialization.
 */
public class NewDeploymentViewModel extends AndroidViewModel {

    private final DeploymentManager deploymentManager;
    private final PocketServerDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Uri> selectedFolderUri = new MutableLiveData<>(null);
    private final MutableLiveData<ProjectValidator.ValidationResult> validationResult = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isValidating = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isDeploying = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> deploymentFinished = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public NewDeploymentViewModel(@NonNull Application application) {
        super(application);
        this.deploymentManager = DeploymentManager.getInstance(application);
        this.database = PocketServerDatabase.getInstance(application);
    }

    public NewDeploymentViewModel(
            @NonNull Application application,
            @NonNull DeploymentManager deploymentManager,
            @NonNull PocketServerDatabase database) {
        super(application);
        this.deploymentManager = deploymentManager;
        this.database = database;
    }

    @NonNull
    public LiveData<Uri> getSelectedFolderUri() {
        return selectedFolderUri;
    }

    @NonNull
    public LiveData<ProjectValidator.ValidationResult> getValidationResult() {
        return validationResult;
    }

    @NonNull
    public LiveData<Boolean> getIsValidating() {
        return isValidating;
    }

    @NonNull
    public LiveData<Boolean> getIsDeploying() {
        return isDeploying;
    }

    @NonNull
    public LiveData<Boolean> getDeploymentFinished() {
        return deploymentFinished;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void onFolderSelected(@NonNull Uri treeUri) {
        selectedFolderUri.setValue(treeUri);
        isValidating.setValue(true);
        errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                ProjectValidator.ValidationResult result =
                        ProjectValidator.validate(getApplication(), treeUri);
                validationResult.postValue(result);
                if (!result.isValid()) {
                    errorMessage.postValue(result.getErrorMessage());
                }
            } catch (Exception e) {
                errorMessage.postValue("Folder validation failed: " + e.getMessage());
            } finally {
                isValidating.postValue(false);
            }
        });
    }

    public void deploy(
            @NonNull String projectName,
            @NonNull HostingMode mode,
            String preferredSubdomain,
            int port,
            boolean spaFallback,
            boolean keepAwake) {
        Uri rootUri = selectedFolderUri.getValue();
        if (rootUri == null) {
            errorMessage.setValue("Please select a website directory first.");
            return;
        }

        isDeploying.setValue(true);
        errorMessage.setValue(null);

        String projectId = "proj_" + UUID.randomUUID().toString().substring(0, 8);
        String finalProjectName = projectName.trim().isEmpty() ? "Website" : projectName.trim();

        DeploymentConfig.Builder configBuilder = new DeploymentConfig.Builder()
                .setProjectId(projectId)
                .setProjectName(finalProjectName)
                .setRootUri(rootUri)
                .setHostingMode(mode)
                .setLocalPort(port > 0 ? port : 8080)
                .setSpaFallbackEnabled(spaFallback)
                .setKeepAwakeWhileLocked(keepAwake);

        if (mode == HostingMode.PUBLIC && preferredSubdomain != null && !preferredSubdomain.trim().isEmpty()) {
            configBuilder.setPublicHostname(preferredSubdomain.trim().toLowerCase());
        }

        DeploymentConfig config = configBuilder.build();

        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                ProjectEntity project = new ProjectEntity(
                        projectId,
                        "device_user",
                        finalProjectName,
                        rootUri.toString(),
                        now
                );
                database.projectDao().insert(project);

                String deploymentId = "dep_" + UUID.randomUUID().toString().substring(0, 8);
                DeploymentEntity deployment = new DeploymentEntity(
                        deploymentId,
                        projectId,
                        "device_primary",
                        1,
                        mode.name(),
                        config.getPublicHostname(),
                        "STARTING",
                        spaFallback,
                        0,
                        0,
                        now,
                        0
                );
                database.deploymentDao().insert(deployment);

                // Start deployment in service
                deploymentManager.startDeployment(config);
                deploymentFinished.postValue(true);
            } catch (Exception e) {
                errorMessage.postValue("Deployment start failed: " + e.getMessage());
            } finally {
                isDeploying.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
