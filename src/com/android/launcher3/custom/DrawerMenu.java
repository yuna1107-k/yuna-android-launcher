package com.android.launcher3.custom;

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;

import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AppsSearchContainerLayout;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 3-dot menu button at the end of the app drawer search bar, with Home settings, wallpaper and
 * widgets.
 */
public final class DrawerMenu {

    private DrawerMenu() { }

    /**
     * Adds the menu button next to {@code searchContainer} inside the drawer. The search bar
     * shrinks to make room and keeps the button aligned with it.
     */
    public static void attach(ViewGroup appsView, View searchContainer) {
        if (!(searchContainer instanceof AppsSearchContainerLayout searchBar)
                || !(ActivityContext.lookupContextNoThrow(appsView.getContext())
                        instanceof Launcher)) {
            return;
        }
        View button = LayoutInflater.from(appsView.getContext())
                .inflate(R.layout.all_apps_menu_button, appsView, false);
        button.setOnClickListener(DrawerMenu::show);
        // Read right after the search bar by accessibility services.
        button.setAccessibilityTraversalAfter(searchContainer.getId());
        appsView.addView(button);
        searchBar.setEndButton(button);
    }

    private static void show(View anchor) {
        if (!(ActivityContext.lookupContextNoThrow(anchor.getContext())
                instanceof Launcher launcher)) {
            return;
        }
        List<OptionItem> options = new ArrayList<>();
        options.add(item(launcher, R.string.settings_button_text, R.drawable.ic_setting,
                v -> {
                    CustomSettings.openSettings(launcher);
                    return true;
                }));
        options.add(item(launcher, R.string.styles_wallpaper_button_text, R.drawable.ic_palette,
                OptionsPopupView::startWallpaperPicker));
        if (WIDGETS_ENABLED) {
            options.add(item(launcher, R.string.widget_button_text, R.drawable.ic_widget,
                    v -> {
                        // Close the drawer first; a state change closes open sheets.
                        launcher.getStateManager().goToState(NORMAL, true,
                                AnimatorListeners.forSuccessCallback(
                                        () -> OptionsPopupView.openWidgets(launcher)));
                        return true;
                    }));
        }

        int[] pos = new int[2];
        float scale = launcher.getDragLayer().getDescendantCoordRelativeToSelf(anchor, pos);
        RectF target = new RectF(pos[0], pos[1],
                pos[0] + anchor.getWidth() * scale, pos[1] + anchor.getHeight() * scale);
        OptionsPopupView.show(launcher, target, options, true);
    }

    private static OptionItem item(Launcher launcher, int label, int icon,
            View.OnLongClickListener action) {
        return new OptionItem(launcher, label, icon, IGNORE, action);
    }
}
