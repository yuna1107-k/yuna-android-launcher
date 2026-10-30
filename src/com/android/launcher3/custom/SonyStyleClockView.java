package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

import java.util.Calendar;
import java.util.Locale;

/**
 * Clock in the style of the Xperia home screen clock: large hours with smaller minutes to the
 * upper right, their tops aligned. Drawn directly so the alignment is exact. Uses a thin sans
 * like the original and the wallpaper-based theme accent color.
 */
public class SonyStyleClockView extends View {

    private static final float MINUTES_RATIO = 0.4f;
    private static final String DIGITS = "0123456789";

    private final Paint mHourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMinutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mBounds = new Rect();
    private String mHour = "";
    private String mMinute = "";
    private int mHourDigitTop;
    private int mMinuteDigitTop;
    private int mHourDigitHeight;

    public SonyStyleClockView(Context context) {
        super(context);
        int color = themeColor(context);
        int shadow = Themes.getAttrColor(context, R.attr.workspaceShadowColor);
        float density = getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[] {mHourPaint, mMinutePaint}) {
            // Thin system sans like the Xperia clock (only the clock digits; other text is Mushin).
            p.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            p.setColor(color);
            p.setShadowLayer(3 * density, 0, density / 2, shadow);
        }
        setHourTextSize(72);
        update();
    }

    /** Sets the size of the hours (sp); minutes scale with it. */
    public void setHourTextSize(float sp) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
        mHourPaint.setTextSize(px);
        mMinutePaint.setTextSize(px * MINUTES_RATIO);
        // Distance from the top of the tallest digit to the baseline, for top alignment.
        mHourPaint.getTextBounds(DIGITS, 0, DIGITS.length(), mBounds);
        mHourDigitTop = -mBounds.top;
        mHourDigitHeight = mBounds.height();
        mMinutePaint.getTextBounds(DIGITS, 0, DIGITS.length(), mBounds);
        mMinuteDigitTop = -mBounds.top;
        requestLayout();
        invalidate();
    }

    /** Refreshes the time; call every minute. */
    public void update() {
        Calendar now = Calendar.getInstance();
        boolean is24 = DateFormat.is24HourFormat(getContext());
        int hour = is24 ? now.get(Calendar.HOUR_OF_DAY)
                : (now.get(Calendar.HOUR) == 0 ? 12 : now.get(Calendar.HOUR));
        String h = String.format(Locale.getDefault(), "%d", hour);
        String m = String.format(Locale.getDefault(), "%02d", now.get(Calendar.MINUTE));
        if (!h.equals(mHour) || !m.equals(mMinute)) {
            boolean widthMayChange = h.length() != mHour.length();
            mHour = h;
            mMinute = m;
            setContentDescription(DateFormat.getTimeFormat(getContext()).format(now.getTime()));
            if (widthMayChange) {
                requestLayout();
            }
            invalidate();
        }
    }

    private float gap() {
        return mMinutePaint.getTextSize() * 0.15f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float width = mHourPaint.measureText(mHour) + gap() + mMinutePaint.measureText(mMinute);
        int height = mHourDigitHeight + Math.round(mHourPaint.getTextSize() * 0.08f);
        setMeasuredDimension(
                resolveSize(Math.round(width) + getPaddingLeft() + getPaddingRight(),
                        widthMeasureSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = getPaddingLeft();
        float hourBaseline = getPaddingTop() + mHourDigitTop;
        canvas.drawText(mHour, x, hourBaseline, mHourPaint);
        x += mHourPaint.measureText(mHour) + gap();
        // Top of the minutes lines up with the top of the hours.
        float minuteBaseline = getPaddingTop() + mMinuteDigitTop;
        canvas.drawText(mMinute, x, minuteBaseline, mMinutePaint);
    }

    /**
     * A light accent tone over dark wallpapers and a dark one over light wallpapers, both from
     * the dynamic (wallpaper based) palette.
     */
    static int themeColor(Context context) {
        int text = Themes.getAttrColor(context, R.attr.workspaceTextColor);
        boolean lightText = ColorUtils.calculateLuminance(text) > 0.5;
        return context.getColor(lightText
                ? android.R.color.system_accent1_100 : android.R.color.system_accent1_700);
    }
}
