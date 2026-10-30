package com.android.launcher3.custom;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.Calendar;
import java.util.Locale;

/**
 * Niagara-style glance panel on the first home page: a Sony-style clock (or a clock widget from
 * another app), the date with the current weather, and the next calendar event. Tapping the
 * date, weather or event opens the week panel.
 */
public class GlanceView extends LinearLayout {

    private static final String TAG = "GlanceView";
    private static final int PERMISSION_REQUEST_CODE = 0x6c61;
    private static final long SOON_MINUTES = 60;
    private static final String KEY_PERMISSIONS_ASKED = "pref_glance_permissions_asked";

    private final FrameLayout mClockContainer;
    private final SonyStyleClockView mClock;
    private final GlanceClockWidget mClockWidget;
    @Nullable private View mShownClock;
    private final TextView mDate;
    private final TextView mWeather;
    private final TextView mAlarm;
    private final TextView mEvent;

    private final BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDateAndEvents();
        }
    };

    private final ContentObserver mCalendarObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            MAIN_EXECUTOR.execute(GlanceView.this::refreshDateAndEvents);
        }
    };

    public GlanceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        // Laid out like the Xperia home clock: everything centered under the clock.
        setGravity(Gravity.CENTER);
        int pad = dp(8);
        setPadding(pad, 0, pad, 0);

        mClockContainer = new FrameLayout(context);
        LayoutParams clockLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        clockLp.gravity = Gravity.CENTER_HORIZONTAL;
        addView(mClockContainer, clockLp);
        mClock = new SonyStyleClockView(context);
        mClock.setOnClickListener(v -> startSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        mClockWidget = new GlanceClockWidget(context);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.CENTER_HORIZONTAL;
        addView(row, rowLp);
        mDate = styled(new TextView(context), 17);
        row.addView(mDate);
        mWeather = styled(new TextView(context), 17);
        LayoutParams weatherLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        weatherLp.setMarginStart(dp(12));
        row.addView(mWeather, weatherLp);

        mAlarm = styled(new TextView(context), 15);
        mAlarm.setGravity(Gravity.CENTER);
        LayoutParams alarmLp = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        alarmLp.topMargin = dp(2);
        addView(mAlarm, alarmLp);
        mAlarm.setOnClickListener(v -> startSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));

        mEvent = styled(new TextView(context), 15);
        mEvent.setGravity(Gravity.CENTER);
        LayoutParams eventLp = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        eventLp.topMargin = dp(2);
        addView(mEvent, eventLp);

        OnClickListener openWeek = v -> onDetailsClicked();
        mDate.setOnClickListener(openWeek);
        mWeather.setOnClickListener(openWeek);
        mEvent.setOnClickListener(openWeek);

        updateClock();
    }

    private TextView styled(TextView view, int sp) {
        CustomFonts.apply(view);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        view.setShadowLayer(dp(3), 0, dp(1) / 2f,
                Themes.getAttrColor(getContext(), R.attr.workspaceShadowColor));
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    /** Uses a smaller clock when the search bar shares the space. */
    public void setCompact(boolean compact) {
        // Fixed size so the panel's measured height (and rows) stays stable.
    }

    /** Shows the built-in clock or the clock widget picked in settings. */
    private void updateClock() {
        AppWidgetHostView widget = mClockWidget.getView();
        View clock = widget != null ? widget : mClock;
        if (clock == mShownClock) {
            return;
        }
        mClockContainer.removeAllViews();
        if (clock.getParent() instanceof ViewGroup parent) {
            parent.removeView(clock);
        }
        mClockContainer.addView(clock, new FrameLayout.LayoutParams(
                widget != null ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT,
                widget != null ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT));
        // A widget fills the space above the date; the built-in clock wraps its content.
        LayoutParams lp = (LayoutParams) mClockContainer.getLayoutParams();
        lp.width = widget != null ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        lp.height = widget != null ? 0 : LayoutParams.WRAP_CONTENT;
        lp.weight = widget != null ? 1 : 0;
        mClockContainer.setLayoutParams(lp);
        mShownClock = clock;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && mShownClock != mClock) {
            mClockWidget.updateSize(mClockContainer.getWidth(), mClockContainer.getHeight());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mClockWidget.startListening();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        getContext().registerReceiver(mTimeReceiver, filter);
        if (CalendarRepository.hasPermission(getContext())) {
            try {
                getContext().getContentResolver().registerContentObserver(
                        CalendarContract.CONTENT_URI, true, mCalendarObserver);
            } catch (SecurityException e) {
                Log.w(TAG, "Unable to observe calendar", e);
            }
        }
        refreshAll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mClockWidget.stopListening();
        getContext().unregisterReceiver(mTimeReceiver);
        getContext().getContentResolver().unregisterContentObserver(mCalendarObserver);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            // e.g. back from settings, the permission dialog or another app.
            refreshAll();
        }
    }

    private void refreshAll() {
        updateClock();
        maybeAskForPermissionsOnce();
        refreshDateAndEvents();
        updateWeather(WeatherRepository.getCached(getContext()));
        WeatherRepository.refreshIfStale(getContext(), this::updateWeather);
    }

    // ---------------------------------------------------------------------------------------
    // Date and calendar

    private void refreshDateAndEvents() {
        mClock.update();
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEE");
        mDate.setText(DateFormat.format(pattern, System.currentTimeMillis()));
        updateAlarm();

        if (!CalendarRepository.hasPermission(getContext())) {
            mEvent.setText(R.string.glance_grant_permission);
            return;
        }
        Context context = getContext();
        UI_HELPER_EXECUTOR.execute(() -> {
            CalendarRepository.Event event = CalendarRepository.nextEvent(context);
            MAIN_EXECUTOR.execute(() -> mEvent.setText(event != null
                    ? describe(context, event) : context.getString(R.string.glance_no_events)));
        });
    }

    private static String describe(Context context, CalendarRepository.Event e) {
        if (e.allDay) {
            return context.getString(R.string.glance_event_all_day, e.title);
        }
        long now = System.currentTimeMillis();
        if (e.begin <= now) {
            return context.getString(R.string.glance_event_now, e.title);
        }
        long minutes = (e.begin - now) / 60_000;
        if (minutes < SOON_MINUTES) {
            return context.getString(R.string.glance_event_in_minutes, (int) minutes + 1, e.title);
        }
        String time = DateFormat.getTimeFormat(context).format(e.begin);
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(e.begin);
        boolean today = start.get(Calendar.DAY_OF_YEAR)
                == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return today ? time + "  " + e.title
                : context.getString(R.string.glance_event_tomorrow, time, e.title);
    }

    /** Next alarm, like "⏰ 6:00 (土)". */
    private void updateAlarm() {
        AlarmManager am = getContext().getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo next = am != null ? am.getNextAlarmClock() : null;
        if (next == null) {
            mAlarm.setVisibility(GONE);
            return;
        }
        String day = DateFormat.format(
                DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE"),
                next.getTriggerTime()).toString();
        mAlarm.setText(getContext().getString(R.string.glance_alarm,
                DateFormat.getTimeFormat(getContext()).format(next.getTriggerTime()), day));
        android.graphics.drawable.Drawable icon = getContext().getDrawable(R.drawable.ic_glance_alarm);
        if (icon != null) {
            int size = Math.round(mAlarm.getTextSize());
            icon.setBounds(0, 0, size, size);
            icon.setTint(mAlarm.getCurrentTextColor());
            mAlarm.setCompoundDrawablesRelative(icon, null, null, null);
            mAlarm.setCompoundDrawablePadding(dp(4));
        }
        mAlarm.setVisibility(VISIBLE);
    }

    // ---------------------------------------------------------------------------------------
    // Weather

    private void updateWeather(@Nullable WeatherRepository.Weather weather) {
        if (weather == null) {
            mWeather.setVisibility(GONE);
            return;
        }
        mWeather.setVisibility(VISIBLE);
        mWeather.setText(getContext().getString(R.string.glance_weather, weather.symbol(),
                weather.temperature, weather.high, weather.low));
    }

    // ---------------------------------------------------------------------------------------
    // Clicks and permissions

    private void onDetailsClicked() {
        if (!CalendarRepository.hasPermission(getContext())
                || !WeatherRepository.hasLocationPermission(getContext())) {
            requestPermissions();
        } else if (ActivityContext.lookupContextNoThrow(getContext()) instanceof Launcher l) {
            CalendarPanel.show(l);
        }
    }

    /** Asks for calendar and location access the first time the panel is shown. */
    private void maybeAskForPermissionsOnce() {
        if (CalendarRepository.hasPermission(getContext())
                && WeatherRepository.hasLocationPermission(getContext())) {
            return;
        }
        SharedPreferences prefs = LauncherPrefs.getPrefs(getContext());
        if (prefs.getBoolean(KEY_PERMISSIONS_ASKED, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_PERMISSIONS_ASKED, true).apply();
        // Wait until the launcher is fully shown.
        postDelayed(this::requestPermissions, 500);
    }

    private void requestPermissions() {
        // The view's context may be a theme wrapper around the launcher activity.
        if (ActivityContext.lookupContextNoThrow(getContext()) instanceof Activity activity) {
            activity.requestPermissions(new String[] {
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            }, PERMISSION_REQUEST_CODE);
        }
    }

    private void startSafely(Intent intent) {
        try {
            getContext().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to open " + intent, e);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
