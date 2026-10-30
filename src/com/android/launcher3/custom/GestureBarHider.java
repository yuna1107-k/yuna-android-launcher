package com.android.launcher3.custom;

import android.app.Activity;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Hides the gesture bar (navigation handle) while the home screen is shown. A swipe from the
 * bottom edge brings it back temporarily; navigation gestures keep working.
 */
public final class GestureBarHider {

    private GestureBarHider() { }

    /** Applies the Home settings choice to the launcher window. */
    public static void apply(Activity activity) {
        WindowInsetsController controller = activity.getWindow().getInsetsController();
        if (controller == null) {
            return;
        }
        if (CustomSettings.isGestureBarHidden(activity)) {
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.navigationBars());
        } else {
            controller.show(WindowInsets.Type.navigationBars());
        }
    }
}
