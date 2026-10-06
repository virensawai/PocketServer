package com.example.pocketserver.ui.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.RequestLogItem;
import com.example.pocketserver.engine.service.DeploymentManager;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for MainViewModel.
 */
public class MainViewModelTest {

    private DeploymentManager deploymentManager;
    private MainViewModel viewModel;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });

        deploymentManager = DeploymentManager.getInstance(new Application());
        viewModel = new MainViewModel(new Application(), deploymentManager);
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void testInitialStateIsStopped() {
        assertNotNull(viewModel.getDeploymentState());
        assertEquals(DeploymentState.STOPPED, viewModel.getDeploymentState().getValue());
    }

    @Test
    public void testRecentRequestsObservation() {
        assertNotNull(viewModel.getRecentRequests());
        assertTrue(viewModel.getRecentRequests().getValue().isEmpty());

        RequestLogItem item = new RequestLogItem(
                "req_1", "GET", "/index.html", 200, 1024, 15, System.currentTimeMillis()
        );
        deploymentManager.notifyRequestLogged(item);

        List<RequestLogItem> list = viewModel.getRecentRequests().getValue();
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("/index.html", list.get(0).getPath());
    }

    @Test
    public void testStopDeployment() {
        viewModel.stopDeployment();
        assertEquals(DeploymentState.STOPPED, viewModel.getDeploymentState().getValue());
    }
}
