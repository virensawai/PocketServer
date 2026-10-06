package com.example.pocketserver.core.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.pocketserver.core.data.entity.DeploymentEntity;
import java.util.List;

/**
 * Data Access Object for DeploymentEntity.
 */
@Dao
public interface DeploymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DeploymentEntity deployment);

    @Update
    void update(DeploymentEntity deployment);

    @Query("SELECT * FROM deployments ORDER BY createdAt DESC")
    List<DeploymentEntity> getAllDeployments();

    @Query("SELECT * FROM deployments WHERE deploymentId = :id")
    DeploymentEntity getDeploymentById(String id);

    @Query("SELECT * FROM deployments WHERE projectId = :projectId ORDER BY version DESC")
    List<DeploymentEntity> getDeploymentsForProject(String projectId);

    @Query("UPDATE deployments SET status = :status WHERE deploymentId = :id")
    void updateStatus(String id, String status);

    @Query("UPDATE deployments SET dataTransferredBytes = :bytes, requestCount = :requests WHERE deploymentId = :id")
    void updateMetrics(String id, long bytes, long requests);

    @Delete
    void delete(DeploymentEntity deployment);

    @Query("DELETE FROM deployments")
    void deleteAll();
}
