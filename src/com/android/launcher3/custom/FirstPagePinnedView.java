package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;

/**
 * The item pinned to the first home page: the glance panel above the search bar.
 *
 * - Long-press lifts it; drag up or down to move it (snaps to the nearest row on release),
 *   or release without moving to get the menu for the part under the finger.
 * - It takes only as many rows as its content needs, at most half the screen height.
 */
public class FirstPagePinnedView extends FrameLayout {

    private static final int SEARCH_BAR_HEIGHT_DP = 56;
    private static final float MAX_HEIGHT_FRACTION = 0.5f;

    private final CheckLongPressHelper mLongPressHelper;
    private final float mTouchSlop;
    private float mDownY;
    private boolean mDragging;

    public FirstPagePinnedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLongPressHelper = new CheckLongPressHelper(this, v -> {
            lift();
            return true;
        });
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** Returns the pinned view containing {@code child}, if any. */
    @Nullable
    public static FirstPagePinnedView from(View child) {
        ViewParent p = child.getParent();
        while (p != null && !(p instanceof FirstPagePinnedView)) {
            p = p.getParent();
        }
        return (FirstPagePinnedView) p;
    }

    /** Shows the parts enabled in settings. */
    public void update() {
        Context context = getContext();
        boolean glance = CustomSettings.isGlanceOnHome(context);
        boolean search = CustomSettings.isSearchBarOnHome(context);

        View glanceView = findViewById(R.id.glance_view);
        View searchBar = findViewById(R.id.first_page_search_bar);
        glanceView.setVisibility(glance ? VISIBLE : GONE);
        searchBar.setVisibility(search ? VISIBLE : GONE);

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) searchBar.getLayoutParams();
        if (glance) {
            lp.height = dp(SEARCH_BAR_HEIGHT_DP);
            lp.weight = 0;
        } else {
            lp.height = 0;
            lp.weight = 1;
        }
        searchBar.setLayoutParams(lp);
        setTranslationY(0);
        setForeground(null);
        post(this::updateNeededRows);
    }

    // ---------------------------------------------------------------------------------------
    // Size: take as few rows as the content needs

    private void updateNeededRows() {
        View glance = findViewById(R.id.glance_view);
        Launcher launcher = launcher();
        if (launcher == null || glance.getVisibility() != VISIBLE || getWidth() == 0) {
            return;
        }
        glance.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int needed = glance.getMeasuredHeight();
        DeviceProfile dp = launcher.getDeviceProfile();
        int cell = dp.cellHeightPx;
        int border = dp.cellLayoutBorderSpacePx.y;
        // Smallest n with n * cell + (n - 1) * border >= needed.
        int rows = Math.max(1, (int) Math.ceil((needed + border) / (float) (cell + border)));
        // At most half the screen height.
        int maxRows = Math.max(1, (int) ((dp.heightPx * MAX_HEIGHT_FRACTION + border)
                / (cell + border)));
        rows = Math.min(rows, maxRows);
        // Triggers a reload when it changes.
        CustomSettings.setGlanceRows(getContext(), rows);
        requestLayout();
    }

    // ---------------------------------------------------------------------------------------
    // Long press and drag

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDownY = ev.getRawY();
            mDragging = false;
        }
        mLongPressHelper.onTouchEvent(ev);
        return mLongPressHelper.hasPerformedLongPress();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mLongPressHelper.hasPerformedLongPress()) {
            mLongPressHelper.onTouchEvent(ev);
            return true;
        }
        float dy = ev.getRawY() - mDownY;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && Math.abs(dy) > mTouchSlop) {
                    startDrag();
                }
                if (mDragging) {
                    setTranslationY(clampDrag(dy));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    endDrag(ev.getActionMasked() == MotionEvent.ACTION_UP);
                } else {
                    drop();
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                        showMenu();
                    }
                }
                mLongPressHelper.cancelLongPress();
                return true;
        }
        return true;
    }

    /** Long press: lift the item to show it can be moved. */
    private void lift() {
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        int accent = getContext().getColor(R.color.materialColorPrimary);
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(20));
        outline.setStroke(dp(2), accent);
        outline.setColor(ColorUtils.setAlphaComponent(accent, 0x22));
        setForeground(outline);
        animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start();
    }

    private void drop() {
        animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(150).start();
        setForeground(null);
    }

    private void startDrag() {
        mDragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void endDrag(boolean commit) {
        mDragging = false;
        animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        int rowHeight = rowHeight();
        int delta = commit && rowHeight > 0 ? Math.round(getTranslationY() / rowHeight) : 0;
        if (delta == 0) {
            animate().translationY(0).setDuration(150).start();
            setForeground(null);
            return;
        }
        int numRows = LauncherAppState.getIDP(getContext()).numRows;
        int row = CustomSettings.getFirstPagePinnedItemRow(getContext(), numRows) + delta;
        // Snap visually, then save; the reload re-lays out the page at the new row.
        animate().translationY(delta * rowHeight).setDuration(120)
                .withEndAction(() -> CustomSettings.setFirstPagePinnedItemRow(getContext(), row))
                .start();
    }

    /** Limits dragging to the rows the item can move to. */
    private float clampDrag(float dy) {
        int rowHeight = rowHeight();
        int numRows = LauncherAppState.getIDP(getContext()).numRows;
        int row = CustomSettings.getFirstPagePinnedItemRow(getContext(), numRows);
        int max = numRows - CustomSettings.getFirstPagePinnedItemRows(getContext());
        return Math.max(-row * rowHeight, Math.min((max - row) * rowHeight, dy));
    }

    private int rowHeight() {
        Launcher launcher = launcher();
        if (launcher == null) {
            return 0;
        }
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.cellHeightPx + dp.cellLayoutBorderSpacePx.y;
    }

    private boolean showMenu() {
        View glance = findViewById(R.id.glance_view);
        View search = findViewById(R.id.first_page_search_bar);
        boolean overSearch = search.getVisibility() == VISIBLE && isUnder(search);
        return overSearch || glance.getVisibility() != VISIBLE
                ? SearchBarOptions.show(search) : SearchBarOptions.showForGlance(glance);
    }

    private boolean isUnder(View child) {
        Rect r = new Rect();
        child.getGlobalVisibleRect(r);
        return r.contains(r.centerX(), Math.round(mDownY));
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        if (!mDragging) {
            mLongPressHelper.cancelLongPress();
        }
    }

    @Nullable
    private Launcher launcher() {
        return ActivityContext.lookupContextNoThrow(getContext()) instanceof Launcher l ? l : null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
