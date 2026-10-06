package com.example.pocketserver.ui.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.pocketserver.engine.model.Deployment;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.DeploymentTelemetry;
import com.example.pocketserver.engine.model.RequestLogItem;
import com.example.pocketserver.engine.service.DeploymentManager;
import java.util.List;

/**
 * Primary ViewModel for the main dashboard and lifecycle observation.
 */
public class MainViewModel extends AndroidViewModel {

    private final DeploymentManager deploymentManager;

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.deploymentManager = DeploymentManager.getInstance(application);
    }

    public MainViewModel(@NonNull Application application, @NonNull DeploymentManager deploymentManager) {
        super(application);
        this.deploymentManager = deploymentManager;
    }

    @NonNull
    public LiveData<DeploymentState> getDeploymentState() {
        return deploymentManager.getDeploymentState();
    }

    @NonNull
    public LiveData<Deployment> getActiveDeployment() {
        return deploymentManager.getActiveDeployment();
    }

    @NonNull
    public LiveData<DeploymentTelemetry> getTelemetry() {
        return deploymentManager.getTelemetry();
    }

    @NonNull
    public LiveData<List<RequestLogItem>> getRecentRequests() {
        return deploymentManager.getRecentRequests();
    }

    public void stopDeployment() {
        deploymentManager.stopDeployment();
    }

    public void restartDeployment() {
        deploymentManager.restartDeployment();
    }
}
