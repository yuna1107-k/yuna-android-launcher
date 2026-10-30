package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.android.launcher3.R;

/** The launcher-wide 無心 (Mushin) font, for text the theme's fontFamily doesn't reach. */
public final class CustomFonts {

    private static Typeface sMushin;

    private CustomFonts() { }

    public static Typeface mushin(Context context) {
        if (sMushin == null) {
            sMushin = ResourcesCompat.getFont(context.getApplicationContext(), R.font.mushin);
        }
        return sMushin;
    }

    /** Applies the font to {@code view} and returns it. */
    public static <T extends TextView> T apply(T view) {
        view.setTypeface(mushin(view.getContext()));
        return view;
    }
}
