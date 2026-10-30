package com.android.launcher3.custom;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * Clock in the style of Sony's home screen clock: large hours with small minutes next to them,
 * aligned to the top. Colored with the wallpaper-based theme accent.
 */
public class SonyStyleClockView extends LinearLayout {

    private static final float MINUTES_RATIO = 0.42f;

    private final TextClock mHours;
    private final TextClock mMinutes;

    public SonyStyleClockView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);

        mHours = new TextClock(context);
        mHours.setFormat12Hour("h");
        mHours.setFormat24Hour("H");
        mMinutes = new TextClock(context);
        mMinutes.setFormat12Hour("mm");
        mMinutes.setFormat24Hour("mm");

        int color = themeColor(context);
        float density = getResources().getDisplayMetrics().density;
        for (TextClock clock : new TextClock[] {mHours, mMinutes}) {
            clock.setTextColor(color);
            clock.setIncludeFontPadding(false);
            clock.setShadowLayer(3 * density, 0, density / 2,
                    Themes.getAttrColor(context, R.attr.workspaceShadowColor));
        }
        addView(mHours, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams minutesLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        minutesLp.setMarginStart(Math.round(4 * density));
        addView(mMinutes, minutesLp);
        setHourTextSize(84);
    }

    /** Sets the size of the hours; minutes scale with it. */
    public void setHourTextSize(float sp) {
        mHours.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        mMinutes.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * MINUTES_RATIO);
        // Nudge the minutes down so their top lines up with the top of the hour digits.
        LayoutParams lp = (LayoutParams) mMinutes.getLayoutParams();
        lp.topMargin = Math.round(sp * 0.12f * getResources().getDisplayMetrics().scaledDensity);
        mMinutes.setLayoutParams(lp);
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
