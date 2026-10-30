package com.android.launcher3.custom;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Bundle;
import android.util.SizeF;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Hosts the widget the user picked as the glance panel clock. Uses its own widget host id so the
 * launcher's cleanup of unused home screen widgets leaves it alone.
 */
final class GlanceClockWidget {

    /** Differs from the launcher (1024) and search bar (1026) hosts. */
    static final int HOST_ID = 1030;

    private final Context mContext;
    private final AppWidgetHost mHost;
    @Nullable private AppWidgetHostView mView;
    private int mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    GlanceClockWidget(Context context) {
        mContext = context;
        mHost = new AppWidgetHost(context.getApplicationContext(), HOST_ID);
    }

    void startListening() {
        try {
            mHost.startListening();
        } catch (RuntimeException ignored) { }
    }

    void stopListening() {
        try {
            mHost.stopListening();
        } catch (RuntimeException ignored) { }
    }

    /** Returns the view for the configured widget, or null for the built-in clock. */
    @Nullable
    AppWidgetHostView getView() {
        int id = CustomSettings.getGlanceClockWidgetId(mContext);
        if (id == mWidgetId && mView != null) {
            return mView;
        }
        mWidgetId = id;
        mView = null;
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return null;
        }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(mContext).getAppWidgetInfo(id);
        if (info == null) {
            return null;
        }
        mView = mHost.createView(mContext, id, info);
        return mView;
    }

    /** Tells the widget the size it is shown at. */
    void updateSize(int widthPx, int heightPx) {
        if (mView == null || widthPx <= 0 || heightPx <= 0) {
            return;
        }
        float density = mContext.getResources().getDisplayMetrics().density;
        SizeF size = new SizeF(widthPx / density, heightPx / density);
        mView.updateAppWidgetSize(new Bundle(), List.of(size));
    }
}
