package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

/**
 * The item pinned to the first home page: the glance panel above the search bar. Either part can
 * be turned off in Home settings, and the user can move it up or down by whole rows
 * ("位置を調整" in its long-press menu).
 */
public class FirstPagePinnedView extends FrameLayout {

    private static final int SEARCH_BAR_HEIGHT_DP = 56;

    @Nullable private View mAdjustControls;

    public FirstPagePinnedView(Context context, AttributeSet attrs) {
        super(context, attrs);
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

        GlanceView glanceView = findViewById(R.id.glance_view);
        View searchBar = findViewById(R.id.first_page_search_bar);
        glanceView.setVisibility(glance ? VISIBLE : GONE);
        glanceView.setCompact(search);
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
    }

    // ---------------------------------------------------------------------------------------
    // Position adjustment

    /** Shows ▲ ▼ ✓ controls to move the pinned item between rows. */
    public void startAdjusting() {
        if (mAdjustControls != null) {
            return;
        }
        int accent = getContext().getColor(R.color.materialColorPrimary);
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(20));
        outline.setStroke(dp(2), accent, dp(8), dp(6));
        outline.setColor(ColorUtils.setAlphaComponent(accent, 0x22));
        setForeground(outline);

        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button("▲", R.string.pinned_move_up, accent, v -> move(-1)));
        controls.addView(button("✓", R.string.pinned_move_done, accent, v -> stopAdjusting()));
        controls.addView(button("▼", R.string.pinned_move_down, accent, v -> move(1)));
        addView(controls, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL));
        mAdjustControls = controls;
    }

    public void stopAdjusting() {
        if (mAdjustControls != null) {
            removeView(mAdjustControls);
            mAdjustControls = null;
        }
        setForeground(null);
    }

    private void move(int delta) {
        int numRows = LauncherAppState.getIDP(getContext()).numRows;
        int row = CustomSettings.getFirstPagePinnedItemRow(getContext(), numRows) + delta;
        int max = numRows - CustomSettings.getFirstPagePinnedItemRows(getContext());
        if (row >= 0 && row <= max) {
            // Triggers a reload; this view is reused, so the controls stay.
            CustomSettings.setFirstPagePinnedItemRow(getContext(), row);
        }
    }

    private TextView button(String label, int description, int accent, OnClickListener click) {
        TextView b = new TextView(getContext());
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(getContext().getColor(R.color.materialColorOnPrimary));
        b.setGravity(Gravity.CENTER);
        b.setContentDescription(getContext().getString(description));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(accent);
        b.setBackground(bg);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopAdjusting();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
