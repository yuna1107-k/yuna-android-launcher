package com.android.launcher3.custom;

import android.app.Activity;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Translucent, theme-tinted app drawer background with the wallpaper blurred behind it.
 *
 * The scrim is the dynamic (wallpaper based) surface color lightly tinted with the primary
 * color. When cross-window blur is unavailable (e.g. battery saver), the scrim becomes nearly
 * opaque so the app labels stay readable.
 */
public final class AllAppsBackground {

    private static final float PRIMARY_TINT = 0.12f;
    private static final float SCRIM_ALPHA_LIGHT = 0.68f;
    private static final float SCRIM_ALPHA_DARK = 0.58f;
    private static final float SCRIM_ALPHA_NO_BLUR = 0.94f;
    /** Extra opacity of the header protection drawn behind the search bar while scrolling. */
    private static final float HEADER_EXTRA_ALPHA = 0.18f;
    private static final int MAX_BLUR_RADIUS_DP = 40;

    private final Activity mActivity;
    private final int mMaxBlurRadius;
    private int mBlurRadius = 0;

    public AllAppsBackground(Activity activity) {
        mActivity = activity;
        mMaxBlurRadius = Math.round(
                MAX_BLUR_RADIUS_DP * activity.getResources().getDisplayMetrics().density);
    }

    /** Returns whether the wallpaper can be blurred behind the launcher window. */
    public static boolean isBlurSupported(Context context) {
        WindowManager wm = context.getSystemService(WindowManager.class);
        return wm != null && wm.isCrossWindowBlurEnabled();
    }

    /** Scrim color for the app drawer. */
    public static int getScrimColor(Context context) {
        float alpha = CustomSettings.getDrawerOpacity(context);
        if (alpha < 0) {
            alpha = !isBlurSupported(context) ? SCRIM_ALPHA_NO_BLUR
                    : Utilities.isDarkTheme(context) ? SCRIM_ALPHA_DARK : SCRIM_ALPHA_LIGHT;
        }
        return ColorUtils.setAlphaComponent(getTintedSurface(context), Math.round(alpha * 255));
    }

    /** Header protection color, slightly more opaque than the scrim. */
    public static int getHeaderProtectionColor(Context context) {
        float alpha = Math.min(1f,
                android.graphics.Color.alpha(getScrimColor(context)) / 255f + HEADER_EXTRA_ALPHA);
        return ColorUtils.setAlphaComponent(getTintedSurface(context), Math.round(alpha * 255));
    }

    private static int getTintedSurface(Context context) {
        return ColorUtils.blendARGB(
                context.getColor(R.color.materialColorSurfaceContainer),
                context.getColor(R.color.materialColorPrimary),
                PRIMARY_TINT);
    }

    /**
     * Updates the wallpaper blur for the app drawer transition.
     *
     * @param progress 0 = home screen, 1 = app drawer fully open.
     */
    public void setProgress(float progress) {
        int radius = isBlurSupported(mActivity)
                ? Math.round(mMaxBlurRadius * Utilities.boundToRange(progress, 0f, 1f))
                : 0;
        if (radius == mBlurRadius) {
            return;
        }
        mBlurRadius = radius;
        Window window = mActivity.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (radius > 0) {
            lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        lp.setBlurBehindRadius(radius);
        window.setAttributes(lp);
    }
}
