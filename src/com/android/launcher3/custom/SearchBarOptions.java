package com.android.launcher3.custom;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;

import android.content.Intent;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-press menus for the search bar and glance panel on the home screen. Removed parts can be
 * restored from Home settings.
 */
public final class SearchBarOptions {

    private SearchBarOptions() { }

    /** Menu for the search bar. Returns false if it can't be shown. */
    public static boolean show(View searchBar) {
        Launcher launcher = launcherFor(searchBar);
        if (launcher == null) {
            return false;
        }
        List<OptionItem> options = new ArrayList<>();
        options.add(item(launcher, R.string.search_bar_remove, R.drawable.ic_remove_no_shadow,
                () -> CustomSettings.setSearchBarOnHome(launcher, false)));
        addCommon(launcher, searchBar, options);
        return showPopup(launcher, searchBar, options);
    }

    /** Menu for the glance panel. */
    public static boolean showForGlance(View glance) {
        Launcher launcher = launcherFor(glance);
        if (launcher == null) {
            return false;
        }
        List<OptionItem> options = new ArrayList<>();
        options.add(item(launcher, R.string.glance_change_clock, R.drawable.ic_widget,
                () -> launcher.startActivity(new Intent(launcher, ClockWidgetPickerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        options.add(item(launcher, R.string.glance_hide, R.drawable.ic_remove_no_shadow,
                () -> CustomSettings.setGlanceOnHome(launcher, false)));
        addCommon(launcher, glance, options);
        return showPopup(launcher, glance, options);
    }

    private static void addCommon(Launcher launcher, View anchor, List<OptionItem> options) {
        options.add(item(launcher, R.string.settings_button_text, R.drawable.ic_setting,
                () -> CustomSettings.openSettings(launcher)));
    }

    private static Launcher launcherFor(View view) {
        ActivityContext context = ActivityContext.lookupContextNoThrow(view.getContext());
        // Not in the wallpaper/grid preview, and only on the plain home screen.
        return context instanceof Launcher launcher && launcher.isInState(NORMAL)
                ? launcher : null;
    }

    private static OptionItem item(Launcher launcher, int label, int icon, Runnable action) {
        return new OptionItem(launcher, label, icon, IGNORE, v -> {
            action.run();
            return true;
        });
    }

    private static boolean showPopup(Launcher launcher, View anchor, List<OptionItem> options) {
        int[] pos = new int[2];
        float scale = launcher.getDragLayer().getDescendantCoordRelativeToSelf(anchor, pos);
        RectF target = new RectF(pos[0], pos[1],
                pos[0] + anchor.getWidth() * scale, pos[1] + anchor.getHeight() * scale);
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        OptionsPopupView.show(launcher, target, options, true);
        return true;
    }
}
