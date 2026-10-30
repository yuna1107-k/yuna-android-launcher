package com.android.launcher3.custom;

import static com.android.launcher3.LauncherState.NORMAL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.TouchController;

import java.lang.reflect.Method;

/**
 * Opens the notification shade when the user swipes down on the home screen.
 */
public class StatusBarSwipeController implements TouchController {

    private static final String TAG = "StatusBarSwipeController";

    /** The downward movement must be this many times larger than the sideways movement. */
    private static final float VERTICAL_RATIO = 1.5f;

    private final Launcher mLauncher;
    private final float mTouchSlop;
    private final PointF mDownPos = new PointF();
    private boolean mCanIntercept;
    private long mDownTime;

    public StatusBarSwipeController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = 2 * ViewConfiguration.get(launcher).getScaledTouchSlop();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mCanIntercept = canIntercept(ev);
                mDownTime = ev.getDownTime();
                mDownPos.set(ev.getX(), ev.getY());
                return false;
            case MotionEvent.ACTION_MOVE:
                // Only act on the gesture whose DOWN we saw; otherwise mDownPos is stale.
                if (!mCanIntercept || ev.getDownTime() != mDownTime) {
                    mCanIntercept = false;
                    return false;
                }
                if (ev.getPointerCount() > 1) {
                    mCanIntercept = false;
                    return false;
                }
                float dx = ev.getX() - mDownPos.x;
                float dy = ev.getY() - mDownPos.y;
                if (dy > mTouchSlop && dy > Math.abs(dx) * VERTICAL_RATIO) {
                    mCanIntercept = false;
                    expandNotifications(mLauncher);
                    return true;
                }
                if (Math.abs(dx) > mTouchSlop || -dy > mTouchSlop) {
                    // Horizontal or upward gesture: leave it to the workspace / all apps.
                    mCanIntercept = false;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mCanIntercept = false;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        // The shade has been opened already; swallow the rest of the gesture.
        return true;
    }

    private boolean canIntercept(MotionEvent ev) {
        return ev.getPointerCount() == 1
                && CustomSettings.isSwipeDownNotificationsEnabled(mLauncher)
                && mLauncher.isInState(NORMAL)
                && !mLauncher.getDragController().isDragging()
                && AbstractFloatingView.getTopOpenView(mLauncher) == null;
    }

    /** Expands the notification shade. Requires android.permission.EXPAND_STATUS_BAR. */
    @SuppressLint("WrongConstant")
    public static void expandNotifications(Context context) {
        try {
            Object statusBar = context.getSystemService("statusbar");
            Method expand = statusBar.getClass().getMethod("expandNotificationsPanel");
            expand.invoke(statusBar);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Unable to expand notification shade", e);
        }
    }
}
