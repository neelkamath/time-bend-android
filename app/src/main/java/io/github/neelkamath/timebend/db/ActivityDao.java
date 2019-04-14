package io.github.neelkamath.timebend.db;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface ActivityDao {
    @Query(Sql.GET)
    LiveData<List<Activity>> getAllLive();

    @Query(Sql.GET)
    List<Activity> getAll();

    @Query("SELECT COUNT(*) FROM activities")
    int getNumberOfActivities();

    @Query("SELECT SUM(duration) FROM activities WHERE is_completed = 0")
    int getIncompleteActivitiesDuration();

    @Query("DELETE FROM activities")
    void deleteAll();

    @Insert
    void insertActivity(Activity activity);

    @Update
    void updateActivity(Activity activity);

    @Delete
    void deleteActivity(Activity activity);
}
