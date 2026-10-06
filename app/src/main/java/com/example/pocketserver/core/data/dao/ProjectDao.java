package com.example.pocketserver.core.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.pocketserver.core.data.entity.ProjectEntity;
import java.util.List;

/**
 * Data Access Object for ProjectEntity.
 */
@Dao
public interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProjectEntity project);

    @Update
    void update(ProjectEntity project);

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    List<ProjectEntity> getAllProjects();

    @Query("SELECT * FROM projects WHERE projectId = :id")
    ProjectEntity getProjectById(String id);

    @Delete
    void delete(ProjectEntity project);

    @Query("DELETE FROM projects")
    void deleteAll();
}
