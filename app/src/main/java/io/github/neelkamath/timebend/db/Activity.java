package io.github.neelkamath.timebend.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "activities")
public class Activity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    /**
     * Used for the UI's positioning of the activity. It should start from {@code 0}.
     */
    public int position;
    public String task;
    public int duration;
    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    public Activity(String task, int duration, int position, boolean isCompleted) {
        this.task = task;
        this.duration = duration;
        this.position = position;
        this.isCompleted = isCompleted;
    }
}
