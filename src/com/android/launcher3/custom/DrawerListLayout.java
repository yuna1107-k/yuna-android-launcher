package com.android.launcher3.custom;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * App drawer list mode: one app per row with a small icon at the start and the label after it
 * (Niagara-like). Selected with {@link CustomSettings#KEY_DRAWER_LAYOUT}; grid is the default.
 */
public final class DrawerListLayout {

    private DrawerListLayout() { }

    public static boolean isEnabled(Context context) {
        return CustomSettings.isDrawerListLayout(context);
    }

    /** Apps per row in the drawer: 1 in list mode, otherwise the grid's column count. */
    public static int getAppsPerRow(Context context, int gridColumns) {
        return isEnabled(context) ? 1 : gridColumns;
    }

    /** Inflates an app row for list mode. */
    public static BubbleTextView inflateIcon(LayoutInflater inflater, ViewGroup parent) {
        BubbleTextView icon = (BubbleTextView) inflater.inflate(
                R.layout.all_apps_icon_list, parent, false);
        Resources res = icon.getResources();
        // BubbleTextView sets the all apps text size and padding in its constructor; override.
        icon.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                res.getDimension(R.dimen.drawer_list_text_size));
        icon.setCompoundDrawablePadding(
                res.getDimensionPixelSize(R.dimen.drawer_list_icon_padding));
        icon.setCenterVertically(false);
        CustomFonts.apply(icon);
        return icon;
    }

    /** Row height in list mode. */
    public static int getRowHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.drawer_list_row_height);
    }

    /**
     * Rebuilds the drawer after the layout setting changed. The device profile itself doesn't
     * change, so force the launcher to re-apply it (new span count, new views, rebind apps).
     */
    public static void onSettingChanged(Context context) {
        Context appContext = context.getApplicationContext();
        Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext();
        if (launcher != null) {
            launcher.setForceConfigUpdate(true);
        }
        InvariantDeviceProfile.INSTANCE.get(appContext).onConfigChanged(appContext);
    }
}
