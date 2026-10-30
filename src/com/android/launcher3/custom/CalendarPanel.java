package com.android.launcher3.custom;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.views.AbstractSlideInView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Niagara-style bottom sheet with the weather and events for the next 7 days, opened from the
 * glance panel. Swipe down (or tap outside) to close; each day has a button to add an event.
 */
public class CalendarPanel extends AbstractSlideInView<Launcher> {

    private static final String TAG = "CalendarPanel";
    private static final int DAYS = 7;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final float MAX_HEIGHT_FRACTION = 0.75f;
    private static final long CLOSE_DURATION = 300;

    private final Launcher mLauncher;
    private final LinearLayout mDays;
    private final ScrollView mScroll;
    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mCardColor;
    private final int mAccent;

    /** Opens the sheet over the home screen. */
    public static void show(Launcher launcher) {
        AbstractSlideInView.closeAllOpenViews(launcher);
        CalendarPanel panel = new CalendarPanel(launcher);
        panel.attachToContainer();
        panel.mIsOpen = true;
        panel.setTranslationShift(TRANSLATION_SHIFT_CLOSED);
        // Animate once the sheet has been measured.
        panel.mContent.post(() -> panel.setUpDefaultOpenAnimation().start());
    }

    private CalendarPanel(Launcher launcher) {
        super(launcher, null, 0);
        mLauncher = launcher;
        setOrientation(VERTICAL);
        setGravity(Gravity.BOTTOM);

        mOnSurface = launcher.getColor(R.color.materialColorOnSurface);
        mOnSurfaceVariant = launcher.getColor(R.color.materialColorOnSurfaceVariant);
        mCardColor = launcher.getColor(R.color.materialColorSurfaceContainerHigh);
        mAccent = launcher.getColor(R.color.materialColorPrimary);
        Rect insets = launcher.getDeviceProfile().getInsets();

        LinearLayout sheet = new LinearLayout(launcher);
        sheet.setOrientation(VERTICAL);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(launcher.getColor(R.color.materialColorSurfaceContainer));
        float r = dp(28);
        sheetBg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sheetBg);
        sheet.setPadding(0, dp(8), 0, insets.bottom);
        sheet.setClickable(true);

        // Drag handle
        View handle = new View(launcher);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(ColorUtils.setAlphaComponent(mOnSurfaceVariant, 0x66));
        handleBg.setCornerRadius(dp(2));
        handle.setBackground(handleBg);
        LayoutParams handleLp = new LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        sheet.addView(handle, handleLp);

        TextView title = text(launcher.getString(R.string.week_panel_title), 22, mOnSurface);
        title.setPadding(dp(24), dp(4), dp(24), dp(12));
        sheet.addView(title);

        mScroll = new ScrollView(launcher) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int max = Math.round(mLauncher.getDeviceProfile().heightPx * MAX_HEIGHT_FRACTION);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max,
                        MeasureSpec.AT_MOST));
            }
        };
        mScroll.setClipToPadding(false);
        mScroll.setPadding(0, 0, 0, dp(12));
        mDays = new LinearLayout(launcher);
        mDays.setOrientation(VERTICAL);
        mDays.setPadding(dp(12), 0, dp(12), 0);
        mScroll.addView(mDays);
        sheet.addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        mContent = sheet;
        addView(sheet, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        load();
    }

    private void load() {
        Context context = getContext();
        long start = CalendarRepository.startOfToday();
        UI_HELPER_EXECUTOR.execute(() -> {
            List<CalendarRepository.Event> events =
                    CalendarRepository.query(context, start, start + DAYS * DAY_MS);
            Map<String, WeatherRepository.Weather> weather = WeatherRepository.getDaily(context);
            MAIN_EXECUTOR.execute(() -> bind(start, events, weather));
        });
    }

    private void bind(long start, List<CalendarRepository.Event> events,
            Map<String, WeatherRepository.Weather> weather) {
        mDays.removeAllViews();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEE");
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(start);
        for (int i = 0; i < DAYS; i++) {
            long dayStart = day.getTimeInMillis();
            List<CalendarRepository.Event> dayEvents = new ArrayList<>();
            for (CalendarRepository.Event e : events) {
                if (e.isOn(dayStart)) {
                    dayEvents.add(e);
                }
            }
            String label = DateFormat.format(datePattern, dayStart).toString();
            if (i == 0) {
                label = getContext().getString(R.string.week_panel_today, label);
            } else if (i == 1) {
                label = getContext().getString(R.string.week_panel_tomorrow, label);
            }
            mDays.addView(dayCard(dayStart, label, weather.get(iso.format(day.getTime())),
                    dayEvents, i == 0));
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private View dayCard(long dayStart, String label, WeatherRepository.Weather weather,
            List<CalendarRepository.Event> events, boolean today) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(12), dp(8), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mCardColor);
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        // Day header: date, weather, add button
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = text(label, 18, today ? mAccent : mOnSurface);
        header.addView(date, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        if (weather != null) {
            header.addView(text(getContext().getString(R.string.week_panel_weather,
                    weather.symbol(), weather.high, weather.low), 16, mOnSurfaceVariant));
        }
        TextView add = text("＋", 22, mAccent);
        add.setGravity(Gravity.CENTER);
        add.setContentDescription(getContext().getString(R.string.week_panel_add_event));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setShape(GradientDrawable.OVAL);
        addBg.setColor(ColorUtils.setAlphaComponent(mAccent, 0x2A));
        add.setBackground(addBg);
        add.setOnClickListener(v -> addEvent(dayStart, today));
        LayoutParams addLp = new LayoutParams(dp(40), dp(40));
        addLp.setMarginStart(dp(12));
        header.addView(add, addLp);
        card.addView(header);

        if (events.isEmpty()) {
            TextView none = text(getContext().getString(R.string.week_panel_no_events), 15,
                    mOnSurfaceVariant);
            none.setPadding(0, dp(6), 0, 0);
            card.addView(none);
        }
        for (CalendarRepository.Event e : events) {
            card.addView(eventRow(e));
        }
        return card;
    }

    private View eventRow(CalendarRepository.Event e) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), dp(8), 0);

        View dot = new View(getContext());
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(e.color != 0 ? e.color | 0xFF000000 : mAccent);
        dot.setBackground(dotBg);
        LayoutParams dotLp = new LayoutParams(dp(8), dp(8));
        dotLp.setMarginEnd(dp(10));
        row.addView(dot, dotLp);

        String time = e.allDay ? getContext().getString(R.string.week_panel_all_day)
                : DateFormat.getTimeFormat(getContext()).format(e.begin) + "–"
                        + DateFormat.getTimeFormat(getContext()).format(e.end);
        TextView timeView = text(time, 14, mOnSurfaceVariant);
        LayoutParams timeLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        timeLp.setMarginEnd(dp(10));
        row.addView(timeView, timeLp);
        row.addView(text(e.title, 16, mOnSurface),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> startSafely(new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)));
        return row;
    }

    /** Opens the calendar app's new event screen: next full hour today, else 9:00. */
    private void addEvent(long dayStart, boolean today) {
        Calendar begin = Calendar.getInstance();
        if (today) {
            begin.add(Calendar.HOUR_OF_DAY, 1);
        } else {
            begin.setTimeInMillis(dayStart);
            begin.set(Calendar.HOUR_OF_DAY, 9);
        }
        begin.set(Calendar.MINUTE, 0);
        begin.set(Calendar.SECOND, 0);
        begin.set(Calendar.MILLISECOND, 0);
        startSafely(new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                        begin.getTimeInMillis() + 60 * 60 * 1000));
    }

    private void startSafely(Intent intent) {
        try {
            mLauncher.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            Log.w(TAG, "Unable to open " + intent, ex);
        }
    }

    private TextView text(CharSequence value, int sp, int color) {
        TextView view = CustomFonts.apply(new TextView(getContext()));
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------------------------------
    // Sheet behaviour

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // While the list is scrolled down, a downward swipe scrolls it instead of closing.
            mNoIntercept = getPopupContainer().isEventOverView(mScroll, ev)
                    && mScroll.canScrollVertically(-1);
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    @Override
    protected int getScrimColor(Context context) {
        return ColorUtils.setAlphaComponent(0xFF000000, 0x66);
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }
}
