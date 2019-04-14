package io.github.neelkamath.timebend;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.neelkamath.timebend.db.Activity;
import io.github.neelkamath.timebend.db.ActivityDao;
import io.github.neelkamath.timebend.db.AppDatabase;

public class MainActivity extends AppCompatActivity {
    private ActivityDao activityDao;
    private SharedPreferences times;
    private RecyclerView recyclerView;
    private ActivityAdapter activityAdapter;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activityDao = AppDatabase.getInstance(this).activityDao();
        times = getSharedPreferences("times", Context.MODE_PRIVATE);
        recyclerView = findViewById(R.id.recyclerView);
        new ActivityAdapterSetter(this).execute();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setTimes();
            }
        };

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        new AdapterSetter(this).execute();

        setTimes();
        setResetVisibility();

        activityDao.getAllLive().observe(
                this,
                new Observer<List<Activity>>() {
                    @Override
                    public void onChanged(@Nullable List<Activity> activities) {
                        setTimes();
                        setResetVisibility();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        setTimes();
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(broadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(broadcastReceiver);
    }

    public void openHowToGuide(View view) {
        ((TextView) new AlertDialog.Builder(this)
                .setMessage(R.string.instructions_popup)
                .show()
                .findViewById(android.R.id.message)
        ).setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Deletes all the activities from the database
     *
     * @param view the {@link android.view.View} clicked on
     */
    public void resetActivities(View view) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.sure)
                .setPositiveButton(
                        R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new ActivitiesDeleter(MainActivity.this).execute();
                                int size = activityAdapter.activityList.size();
                                activityAdapter.activityList.clear();
                                activityAdapter.notifyItemRangeRemoved(0, size);
                            }
                        }
                )
                .setNegativeButton(R.string.no, null)
                .show();
    }

    public void setStartTime(View view) {
        setDayTime("start");
    }

    public void setEndTime(View view) {
        setDayTime("end");
    }

    /**
     * Creates an activity if it is valid otherwise gives a {@link android.widget.Toast} telling why
     * it isn't valid
     *
     * @param view the {@link android.view.View} clicked on
     */
    public void createActivity(View view) {
        new ActivityDialog(this, null).execute();
    }

    /**
     * Saves the start or end time of the user after prompting the user with a
     * {@link android.widget.TimePicker}.
     *
     * @param type {@code "start"} if it's the start of the day else {@code "end"} for the end of
     *             the day
     */
    private void setDayTime(final String type) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int hour, int min) {
                        times
                                .edit()
                                .putInt(type + "Hour", hour)
                                .putInt(type + "Minute", min)
                                .apply();
                        setTimes();
                    }
                },
                times.getInt(type + "Hour", calendar.get(Calendar.HOUR_OF_DAY)),
                times.getInt(type + "Minute", calendar.get(Calendar.MINUTE)),
                DateFormat.is24HourFormat(this)
        ).show();
    }

    /**
     * @param hour The hour to format to 12- or 24-hour format (according to the user's preference).
     * @return The formatted hour.
     */
    private int formatHour(int hour) {
        final int lenOfDay = 12;
        if (!DateFormat.is24HourFormat(this) && hour > lenOfDay) {
            hour -= lenOfDay;
        }
        return hour;
    }

    /**
     * Sets the visibility of the reset button based on whether there are any activities or not.
     */
    private void setResetVisibility() {
        new VisibilitySetter(this).execute();
    }

    /**
     * Sets the text for start, end and reserve left provided the user has set them.
     */
    private void setTimes() {
        new TimesSetter(this).execute();
    }

    /**
     * Gives a two digit number (e.g., {@code 7} will become {@code "07"} and {@code 334} will
     * become {@code "334"}).
     *
     * @param num number to modify
     * @return two digit number if the number is one digit long else the number passed in
     */
    private String makeTwoDigits(int num) {
        String number = Integer.toString(num);
        return number.length() == 1 ? String.format(Locale.US, "0%s", number) : number;
    }

    private void toggleKeyboardShown() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE
        );
        if (manager != null) {
            manager.toggleSoftInput(0, 0);
        }
    }

    /**
     * If there are no activities, the reset button will be hidden else it will be shown.
     */
    private static class VisibilitySetter extends AsyncTask<Void, Void, Void> {
        WeakReference<MainActivity> reference;

        VisibilitySetter(MainActivity mainActivity) {
            this.reference = new WeakReference<>(mainActivity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final int numOfActivities = reference.get().activityDao.getNumberOfActivities();
            reference.get().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            reference.get().findViewById(R.id.resetButton).setVisibility(
                                    numOfActivities == 0 ? View.INVISIBLE : View.VISIBLE
                            );
                        }
                    }
            );
            return null;
        }
    }

    /**
     * Sets the start, end and reserve times on the UI if applicable.
     */
    private static class TimesSetter extends AsyncTask<Void, Void, Void> {
        private WeakReference<MainActivity> reference;

        TimesSetter(MainActivity mainActivity) {
            this.reference = new WeakReference<>(mainActivity);
        }

        /**
         * Sets the start or end time on the UI.
         *
         * @param isStart whether it's the start button ({@code true} or end button to set
         *                ({@code false}
         * @param hour    the hour to set
         * @param min     the minute to set
         */
        private void setTimesText(final boolean isStart, final int hour, final int min) {
            reference.get().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            int id = isStart ? R.id.startButton : R.id.endButton;
                            final int lenOfDay = 12;
                            ((Button) reference.get().findViewById(id)).setText(
                                    String.format(
                                            Locale.US,
                                            "%s:%s %s",
                                            reference.get().formatHour(hour),
                                            reference.get().makeTwoDigits(min),
                                            hour < lenOfDay ? "AM" : "PM"
                                    )
                            );
                        }
                    }
            );
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final int startHour = reference.get().times.getInt("startHour", -1);
            final int startMin = reference.get().times.getInt("startMinute", -1);
            final int endHour = reference.get().times.getInt("endHour", -1);
            final int endMin = reference.get().times.getInt("endMinute", -1);
            boolean isValidStart = startHour != -1 && startMin != -1;
            boolean isValidEnd = endHour != -1 && endMin != -1;

            if (isValidStart) {
                setTimesText(true, startHour, startMin);
            }
            if (isValidEnd) {
                setTimesText(false, endHour, endMin);
            }


            final TextView reserveLeft = reference.get().findViewById(R.id.reserveLeftTextView);
            if (isValidStart && isValidEnd) {
                final int minutes = 60;
                int startMinutes = startHour * minutes + startMin;
                final int dayInHours = 24;
                int hour = startHour > endHour ? (endHour + dayInHours) : endHour;
                int endMinutes = (hour * minutes) + endMin;
                Calendar calendar = Calendar.getInstance();
                int currMinutes = calendar.get(Calendar.HOUR_OF_DAY) * minutes
                        + calendar.get(Calendar.MINUTE);
                if (currMinutes > startMinutes && currMinutes < endMinutes) {
                    startMinutes = currMinutes;
                }
                int diff = endMinutes - startMinutes
                        - reference.get().activityDao.getIncompleteActivitiesDuration();
                String minus = "";
                if (diff < 0) {
                    minus = "-";
                    diff = Math.abs(diff);
                }
                final String minusSign = minus;
                final int difference = diff;
                reference.get().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                reserveLeft.setVisibility(View.VISIBLE);
                                ((TextView) reference.get().findViewById(R.id.reserveTextView))
                                        .setText(
                                                String.format(
                                                        Locale.US,
                                                        "%s%d:%s",
                                                        minusSign,
                                                        difference / minutes,
                                                        reference.get().makeTwoDigits(
                                                                difference % minutes
                                                        )
                                                )
                                        );
                            }
                        }
                );
            } else {
                reference.get().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                reserveLeft.setVisibility(View.INVISIBLE);
                            }
                        }
                );
            }
            return null;
        }
    }

    /**
     * Inserts an activity into the database.
     */
    private static class ActivityCreator extends AsyncTask<Void, Void, Void> {
        ActivityDao activityDao;
        Activity activity;

        ActivityCreator(ActivityDao activityDao, Activity activity) {
            this.activityDao = activityDao;
            this.activity = activity;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            activityDao.insertActivity(activity);
            return null;
        }
    }

    /**
     * Creates or updates an existing activity.
     */
    private static class ActivityDialog extends AsyncTask<Void, Void, Void> {
        WeakReference<MainActivity> reference;
        Activity activity;

        /**
         * @param mainActivity {@link MainActivity}
         * @param activity     If the task is to be updated, then this should be the task to
         *                     update. Otherwise, this should be {@code null}
         */
        ActivityDialog(MainActivity mainActivity, Activity activity) {
            reference = new WeakReference<>(mainActivity);
            this.activity = activity;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final int numOfActivities = reference.get().activityDao.getNumberOfActivities();

            reference.get().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            View view = View.inflate(
                                    reference.get(),
                                    R.layout.creator_layout,
                                    null
                            );
                            final EditText activityEditText = view.findViewById(
                                    R.id.activityEditText
                            );
                            final EditText durationEditText = view.findViewById(
                                    R.id.durationEditText
                            );
                            final AlertDialog.Builder builder = new AlertDialog.Builder(
                                    reference.get()
                            )
                                    .setView(view)
                                    .setPositiveButton(
                                            activity == null ? R.string.create : R.string.update,
                                            null
                                    )
                                    .setNegativeButton(R.string.cancel, null);

                            final AlertDialog dialog = builder.create();

                            dialog.show();
                            if (activity != null) {
                                activityEditText.setText(activity.task);
                                durationEditText.setText(String.valueOf(activity.duration));
                            }

                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            boolean isValidTxt = true;
                                            boolean isValidDuration;
                                            int msg = activity == null
                                                    ? R.string.created
                                                    : R.string.updated;

                                            String s = activityEditText.getText().toString().trim();
                                            if (s.isEmpty()) {
                                                isValidTxt = false;
                                                msg = R.string.no_activity_specified;
                                            }

                                            String duration = durationEditText.getText().toString();
                                            if (duration.isEmpty()) {
                                                isValidDuration = false;
                                                msg = R.string.duration_empty;
                                            } else {
                                                isValidDuration = true;
                                                int length = Integer.parseInt(duration);
                                                final int oneHour = 60;
                                                if (length < 1 || length > oneHour) {
                                                    isValidDuration = false;
                                                    msg = R.string.invalid_duration_length;
                                                }
                                            }

                                            Toast.makeText(
                                                    reference.get(),
                                                    reference.get().getResources().getString(msg),
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                            if (isValidTxt && isValidDuration) {
                                                int time = Integer.parseInt(
                                                        durationEditText.getText().toString()
                                                );
                                                if (activity == null) {
                                                    new ActivityCreator(
                                                            reference.get().activityDao,
                                                            new Activity(
                                                                    s,
                                                                    time,
                                                                    numOfActivities,
                                                                    false
                                                            )
                                                    ).execute();
                                                } else {
                                                    activity.task = s;
                                                    activity.duration = time;
                                                    new ActivityUpdater(
                                                            reference.get().activityDao,
                                                            activity
                                                    ).execute();
                                                    reference
                                                            .get()
                                                            .activityAdapter
                                                            .notifyItemChanged(activity.position);
                                                }
                                                dialog.dismiss();

                                                reference.get().toggleKeyboardShown();
                                            }
                                        }
                                    }
                            );
                        }
                    }
            );

            return null;
        }
    }

    /**
     * Deletes all the activities.
     */
    private static class ActivitiesDeleter extends AsyncTask<Void, Void, Void> {
        private WeakReference<MainActivity> reference;

        ActivitiesDeleter(MainActivity mainActivity) {
            this.reference = new WeakReference<>(mainActivity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            reference.get().activityDao.deleteAll();
            return null;
        }
    }

    /**
     * Sets {@link #activityAdapter}.
     */
    private static class ActivityAdapterSetter extends AsyncTask<Void, Void, Void> {
        private WeakReference<MainActivity> reference;

        ActivityAdapterSetter(MainActivity mainActivity) {
            reference = new WeakReference<>(mainActivity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            reference.get().activityAdapter = new ActivityAdapter(
                    reference.get().activityDao.getAll(),
                    reference.get()
            );
            return null;
        }
    }

    /**
     * Sets the adapter and {@link android.support.v7.widget.helper.ItemTouchHelper} for the
     * {@link io.github.neelkamath.timebend.MainActivity#recyclerView} using the
     * {@link #activityAdapter}
     */
    private static class AdapterSetter extends AsyncTask<Void, Void, Void> {
        private WeakReference<MainActivity> reference;

        AdapterSetter(MainActivity mainActivity) {
            this.reference = new WeakReference<>(mainActivity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            reference.get().recyclerView.setAdapter(reference.get().activityAdapter);
            final ItemTouchHelper helper = new ItemTouchHelper(
                    new ItemTouchHelper.Callback() {
                        @Override
                        public int getMovementFlags(RecyclerView recyclerView,
                                                    RecyclerView.ViewHolder viewHolder) {
                            return makeMovementFlags(
                                    ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                            );
                        }

                        @Override
                        public boolean onMove(RecyclerView recyclerView,
                                              RecyclerView.ViewHolder viewHolder,
                                              RecyclerView.ViewHolder target) {
                            int fromPosition = viewHolder.getAdapterPosition();
                            int toPosition = target.getAdapterPosition();
                            List<Activity> activityList = reference
                                    .get()
                                    .activityAdapter
                                    .activityList;
                            Activity activity = activityList.get(fromPosition);
                            reference.get().activityAdapter.notifyItemMoved(
                                    fromPosition,
                                    toPosition
                            );
                            activity.position = toPosition;
                            new ActivityUpdater(reference.get().activityDao, activity).execute();

                            activity = activityList.get(toPosition);
                            activity.position = fromPosition;
                            new ActivityUpdater(reference.get().activityDao, activity).execute();
                            Collections.swap(activityList, fromPosition, toPosition);
                            return true;
                        }

                        @Override
                        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                            List<Activity> activityList = reference
                                    .get()
                                    .activityAdapter
                                    .activityList;
                            int index = viewHolder.getAdapterPosition();
                            Activity activity = activityList.get(index);
                            switch (direction) {
                                case ItemTouchHelper.RIGHT:
                                    activity.isCompleted = !activity.isCompleted;
                                    reference.get().activityAdapter.notifyItemChanged(index);
                                    new ActivityUpdater(reference.get().activityDao, activity)
                                            .execute();
                                    break;
                                case ItemTouchHelper.LEFT:
                                    activityList.remove(index);
                                    reference.get().activityAdapter.notifyItemRemoved(index);
                                    new ActivityDeleter(activity, reference.get().activityDao)
                                            .execute();
                                    for (int count = 0; count < activityList.size(); count++) {
                                        Activity a = activityList.get(count);
                                        a.position = count;
                                        new ActivityUpdater(reference.get().activityDao, a)
                                                .execute();
                                    }
                            }
                        }

                        @Override
                        public void onChildDraw(Canvas c, RecyclerView recyclerView,
                                                RecyclerView.ViewHolder viewHolder,
                                                float dX, float dY, int actionState,
                                                boolean isCurrentlyActive) {
                            boolean isRight = dX > 0;
                            Paint paint = new Paint();
                            paint.setColor(isRight ? Color.GREEN : Color.RED);
                            c.drawRect(
                                    isRight ? viewHolder.itemView.getLeft()
                                            : (viewHolder.itemView.getRight() + dX),
                                    viewHolder.itemView.getTop(),
                                    isRight ? dX : viewHolder.itemView.getRight(),
                                    viewHolder.itemView.getBottom(),
                                    paint
                            );
                            Bitmap bitmap = BitmapFactory.decodeResource(
                                    reference.get().getResources(),
                                    isRight ? R.mipmap.check_foreground : R.mipmap.clear_foreground
                            );
                            int i = viewHolder.itemView.getBottom() - viewHolder.itemView.getTop()
                                    - bitmap.getHeight();
                            final int len = i / 2;
                            c.drawBitmap(
                                    bitmap,
                                    isRight ? (dX - bitmap.getWidth())
                                            : (viewHolder.itemView.getRight() + dX),
                                    viewHolder.itemView.getTop() + len,
                                    paint
                            );
                            super.onChildDraw(
                                    c,
                                    recyclerView,
                                    viewHolder,
                                    dX,
                                    dY,
                                    actionState,
                                    isCurrentlyActive
                            );
                        }
                    }
            );
            reference.get().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            helper.attachToRecyclerView(reference.get().recyclerView);
                        }
                    }
            );
            return null;
        }

        private static class ActivityDeleter extends AsyncTask<Void, Void, Void> {
            private Activity activity;
            private ActivityDao activityDao;

            ActivityDeleter(Activity activity, ActivityDao activityDao) {
                this.activity = activity;
                this.activityDao = activityDao;
            }

            @Override
            protected Void doInBackground(Void... voids) {
                activityDao.deleteActivity(activity);
                return null;
            }
        }
    }

    private static class ActivityUpdater extends AsyncTask<Void, Void, Void> {
        private ActivityDao activityDao;
        private Activity activity;

        ActivityUpdater(ActivityDao activityDao, Activity activity) {
            this.activityDao = activityDao;
            this.activity = activity;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            activityDao.updateActivity(activity);
            return null;
        }
    }

    /**
     * Don't call {@link ActivityAdapter#notifyItemInserted(int)} or
     * {@link ActivityAdapter#notifyItemRemoved(int)} as it automatically updates.
     */
    private static class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
        private List<Activity> activityList;
        private MainActivity mainActivity;

        ActivityAdapter(List<Activity> activities, final MainActivity mainActivity) {
            this.activityList = activities;
            this.mainActivity = mainActivity;

            mainActivity.activityDao.getAllLive().observe(
                    this.mainActivity,
                    new Observer<List<Activity>>() {
                        @Override
                        public void onChanged(@Nullable List<Activity> activities) {
                            /*
                            Only update if an task has been added or removed. If updates are
                            allowed otherwise, activities won't be movable. The moving activities
                            logic is implemented for the UI anyways.
                             */
                            if (activities == null || activities.size() != activityList.size()) {
                                activityList = activities;
                                notifyDataSetChanged();
                            }
                        }
                    }
            );
        }

        @Override
        @NonNull
        public ActivityAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ActivityAdapter.ViewHolder(
                    LayoutInflater
                            .from(parent.getContext())
                            .inflate(R.layout.activities_layout, parent, false)
            );
        }

        @Override
        public void onBindViewHolder(@NonNull final ActivityAdapter.ViewHolder holder,
                                     int position) {
            final Activity activity = activityList.get(position);

            holder.activityTextView.setAutoLinkMask(Linkify.ALL);
            holder.activityTextView.setText(activity.task);
            holder.durationTextView.setText(
                    String.format(Locale.US, "%d", activity.duration)
            );

            int color = activity.isCompleted ? Color.LTGRAY : Color.BLACK;
            holder.activityTextView.setTextColor(color);
            holder.durationTextView.setTextColor(color);

            holder.activityTextView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG);
            holder.durationTextView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG);
            if (!activity.isCompleted) {
                holder.activityTextView.setPaintFlags(
                        holder.activityTextView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG
                );
                holder.durationTextView.setPaintFlags(
                        holder.durationTextView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG
                );
            }

            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            new ActivityDialog(mainActivity, activity).execute();
                        }
                    }
            );
        }

        @Override
        public int getItemCount() {
            return activityList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView activityTextView;
            TextView durationTextView;

            ViewHolder(View itemView) {
                super(itemView);

                activityTextView = itemView.findViewById(R.id.activityTextView);
                durationTextView = itemView.findViewById(R.id.durationTextView);
            }
        }
    }
}
