package com.android.launcher3.custom;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.LoaderTask;

/**
 * Preference keys and accessors for the custom home screen features.
 */
public final class CustomSettings {

    /** Whether the search bar is shown on the first home screen page. */
    public static final String KEY_SEARCH_BAR_ON_HOME = LoaderTask.SMARTSPACE_ON_HOME_SCREEN;

    /** Whether the glance panel (date, weather, next event) is shown on the first page. */
    public static final String KEY_GLANCE_ON_HOME = "pref_glance_on_home";

    /** Top row of the search bar / glance panel on the first page. */
    public static final String KEY_PINNED_ITEM_ROW = "pref_pinned_item_row";

    /** Rows the glance panel needs for its content. */
    public static final String KEY_GLANCE_ROWS = "pref_glance_rows";

    /** App widget used as the clock in the glance panel. */
    public static final String KEY_GLANCE_CLOCK_WIDGET = "pref_glance_clock_widget";

    /** Whether swiping down on the home screen opens the notification shade. */
    public static final String KEY_SWIPE_DOWN_NOTIFICATIONS = "pref_swipe_down_notifications";

    /** Whether Google Discover is shown on the screen left of the home screen. */
    public static final String KEY_DISCOVER = "pref_enable_minus_one";

    /** Icon size as a percentage of the grid's default size. */
    public static final String KEY_ICON_SIZE = "pref_icon_size_percent";
    public static final int ICON_SIZE_DEFAULT = 100;
    public static final int ICON_SIZE_MIN = 70;
    public static final int ICON_SIZE_MAX = 130;

    /** Mask shape for adaptive icons: an AppShapesProvider key, or empty for the system shape. */
    public static final String KEY_ICON_SHAPE = "pref_icon_shape";

    /** Package of the selected third-party icon pack, or empty for none. */
    public static final String KEY_ICON_PACK = "pref_icon_pack";

    /** Returns the selected icon shape key, or "" for the system default. */
    public static String getIconShape(Context context) {
        String v = prefs(context).getString(KEY_ICON_SHAPE, "");
        return v == null ? "" : v;
    }

    /** Returns the selected icon pack package, or "" for none. */
    public static String getIconPack(Context context) {
        String v = prefs(context).getString(KEY_ICON_PACK, "");
        return v == null ? "" : v;
    }

    private CustomSettings() { }

    /** Whether the gesture bar (navigation handle) is hidden on the home screen. */
    public static final String KEY_HIDE_GESTURE_BAR = "pref_hide_gesture_bar";

    public static boolean isGestureBarHidden(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_GESTURE_BAR, true);
    }

    /** Opens Home settings. */
    public static void openSettings(Context context) {
        context.startActivity(new android.content.Intent(
                android.content.Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(context.getPackageName())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    /** Opacity of the app drawer background, in percent. */
    public static final String KEY_DRAWER_OPACITY = "pref_drawer_opacity";
    public static final int DRAWER_OPACITY_DEFAULT = -1; // use the built-in default

    /** App drawer layout: "grid" (default) or "list". */
    public static final String KEY_DRAWER_LAYOUT = "pref_drawer_layout";
    public static final String DRAWER_LAYOUT_GRID = "grid";
    public static final String DRAWER_LAYOUT_LIST = "list";

    public static boolean isDrawerListLayout(Context context) {
        return DRAWER_LAYOUT_LIST.equals(
                prefs(context).getString(KEY_DRAWER_LAYOUT, DRAWER_LAYOUT_GRID));
    }

    /** Returns the user's drawer opacity (0..1), or a negative value for the default. */
    public static float getDrawerOpacity(Context context) {
        int percent = prefs(context).getInt(KEY_DRAWER_OPACITY, DRAWER_OPACITY_DEFAULT);
        return percent < 0 ? -1f : Math.max(0, Math.min(100, percent)) / 100f;
    }

    /** Returns the user's icon scale, e.g. 1.1 for 110%. */
    public static float getIconScale(Context context) {
        int percent = prefs(context).getInt(KEY_ICON_SIZE, ICON_SIZE_DEFAULT);
        percent = Math.max(ICON_SIZE_MIN, Math.min(ICON_SIZE_MAX, percent));
        return percent / 100f;
    }

    /** Returns a copy of {@code sizes} multiplied by the user's icon scale. */
    public static float[] scaleIconSizes(Context context, float[] sizes) {
        float scale = getIconScale(context);
        float[] scaled = new float[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            scaled[i] = sizes[i] * scale;
        }
        return scaled;
    }

    private static SharedPreferences prefs(Context context) {
        return LauncherPrefs.getPrefs(context);
    }

    public static void setSearchBarOnHome(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SEARCH_BAR_ON_HOME, show).apply();
    }

    public static boolean isSearchBarOnHome(Context context) {
        return prefs(context).getBoolean(KEY_SEARCH_BAR_ON_HOME, true);
    }

    public static void setGlanceOnHome(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_GLANCE_ON_HOME, show).apply();
    }

    public static boolean isGlanceOnHome(Context context) {
        return prefs(context).getBoolean(KEY_GLANCE_ON_HOME, true);
    }

    /** Whether the first page has a pinned item (search bar and/or glance panel) at the top. */
    public static boolean isFirstPagePinnedItemEnabled(Context context) {
        return isSearchBarOnHome(context) || isGlanceOnHome(context);
    }

    /** Rows the first page pinned item takes: what the glance panel needs, +1 for search. */
    public static int getFirstPagePinnedItemRows(Context context) {
        int rows = (isGlanceOnHome(context) ? prefs(context).getInt(KEY_GLANCE_ROWS, 2) : 0)
                + (isSearchBarOnHome(context) ? 1 : 0);
        return Math.max(1, rows);
    }

    /** Saves the rows the glance panel's content needs (measured on screen). */
    public static void setGlanceRows(Context context, int rows) {
        if (prefs(context).getInt(KEY_GLANCE_ROWS, 2) != rows) {
            prefs(context).edit().putInt(KEY_GLANCE_ROWS, rows).apply();
        }
    }

    /** Top row of the first page pinned item, chosen by the user, within a grid of numRows. */
    public static int getFirstPagePinnedItemRow(Context context, int numRows) {
        int max = Math.max(0, numRows - getFirstPagePinnedItemRows(context));
        return Math.max(0, Math.min(max, prefs(context).getInt(KEY_PINNED_ITEM_ROW, 0)));
    }

    public static void setFirstPagePinnedItemRow(Context context, int row) {
        prefs(context).edit().putInt(KEY_PINNED_ITEM_ROW, Math.max(0, row)).apply();
    }

    /** App widget id used as the glance panel clock, or INVALID_APPWIDGET_ID (0) for built-in. */
    public static int getGlanceClockWidgetId(Context context) {
        return prefs(context).getInt(KEY_GLANCE_CLOCK_WIDGET, 0);
    }

    public static void setGlanceClockWidgetId(Context context, int widgetId) {
        prefs(context).edit().putInt(KEY_GLANCE_CLOCK_WIDGET, widgetId).apply();
    }

    public static boolean isSwipeDownNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SWIPE_DOWN_NOTIFICATIONS, true);
    }

    public static boolean isDiscoverEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DISCOVER, true);
    }
}
