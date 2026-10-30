package com.android.launcher3.custom;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Reads upcoming events from the calendar provider for the glance panel and week panel. */
public final class CalendarRepository {

    private static final String TAG = "CalendarRepository";

    /** One event occurrence. */
    public static final class Event {
        public final long id;
        public final String title;
        public final long begin;
        public final long end;
        public final boolean allDay;
        public final int color;

        Event(long id, @Nullable String title, long begin, long end, boolean allDay, int color) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
            this.color = color;
        }

        /** Whether this event happens on the local day starting at {@code dayStart}. */
        public boolean isOn(long dayStart) {
            long dayEnd = dayStart + 24L * 60 * 60 * 1000;
            if (allDay) {
                // All-day events are stored as UTC midnights.
                Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utc.setTimeInMillis(begin);
                Calendar local = Calendar.getInstance();
                local.setTimeInMillis(dayStart);
                long startDay = dayIndex(utc.get(Calendar.YEAR), utc.get(Calendar.DAY_OF_YEAR));
                utc.setTimeInMillis(end - 1);
                long endDay = dayIndex(utc.get(Calendar.YEAR), utc.get(Calendar.DAY_OF_YEAR));
                long day = dayIndex(local.get(Calendar.YEAR), local.get(Calendar.DAY_OF_YEAR));
                return day >= startDay && day <= endDay;
            }
            return begin < dayEnd && end > dayStart;
        }

        private static long dayIndex(int year, int dayOfYear) {
            return year * 400L + dayOfYear;
        }
    }

    private CalendarRepository() { }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns the start of today (local midnight). */
    public static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Event occurrences overlapping [begin, end), sorted by start time. */
    @WorkerThread
    public static List<Event> query(Context context, long begin, long end) {
        List<Event> events = new ArrayList<>();
        if (!hasPermission(context)) {
            return events;
        }
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, begin);
        ContentUris.appendId(uri, end);
        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
        };
        String selection = CalendarContract.Instances.VISIBLE + "=1 AND "
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!="
                + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED;
        try (Cursor c = context.getContentResolver().query(uri.build(), projection, selection,
                null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext()) {
                events.add(new Event(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3),
                        c.getInt(4) == 1, c.getInt(5)));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Calendar query failed", e);
        }
        return events;
    }

    /** The next timed event that hasn't ended, else today's first all-day event. */
    @WorkerThread
    @Nullable
    public static Event nextEvent(Context context) {
        long now = System.currentTimeMillis();
        long today = startOfToday();
        Event allDayToday = null;
        for (Event e : query(context, now, today + 2L * 24 * 60 * 60 * 1000)) {
            if (e.allDay) {
                if (allDayToday == null && e.isOn(today)) {
                    allDayToday = e;
                }
            } else if (e.end > now) {
                return e;
            }
        }
        return allDayToday;
    }
}
