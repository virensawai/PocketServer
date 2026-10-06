package com.example.pocketserver.core.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.pocketserver.core.data.dao.DeploymentDao;
import com.example.pocketserver.core.data.dao.ProjectDao;
import com.example.pocketserver.core.data.entity.DeploymentEntity;
import com.example.pocketserver.core.data.entity.ProjectEntity;

/**
 * Primary Room database for PocketServer local metadata persistence.
 */
@Database(entities = {ProjectEntity.class, DeploymentEntity.class}, version = 1, exportSchema = false)
public abstract class PocketServerDatabase extends RoomDatabase {

    private static final String DB_NAME = "pocketserver.db";
    private static volatile PocketServerDatabase instance;

    public abstract ProjectDao projectDao();
    public abstract DeploymentDao deploymentDao();

    public static PocketServerDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (PocketServerDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PocketServerDatabase.class,
                            DB_NAME
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }
}
