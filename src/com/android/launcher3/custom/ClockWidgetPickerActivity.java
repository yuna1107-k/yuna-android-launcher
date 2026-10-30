package com.android.launcher3.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lets the user pick another app's widget to use as the clock in the glance panel. Handles the
 * bind permission prompt and the widget's configuration screen, then saves the widget id.
 */
public class ClockWidgetPickerActivity extends Activity {

    private static final String TAG = "ClockWidgetPicker";
    private static final int REQUEST_BIND = 1;
    private static final int REQUEST_CONFIGURE = 2;

    private AppWidgetHost mHost;
    private AppWidgetManager mManager;
    private int mPendingId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AppWidgetProviderInfo mPendingInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHost = new AppWidgetHost(getApplicationContext(), GlanceClockWidget.HOST_ID);
        mManager = AppWidgetManager.getInstance(this);
        if (savedInstanceState == null) {
            showPicker();
        } else {
            mPendingId = savedInstanceState.getInt("pending_id", mPendingId);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("pending_id", mPendingId);
    }

    private void showPicker() {
        List<AppWidgetProviderInfo> providers = new ArrayList<>(
                mManager.getInstalledProvidersForProfile(Process.myUserHandle()));
        Collator collator = Collator.getInstance();
        // Clock widgets first, then alphabetical.
        providers.sort((a, b) -> {
            int ca = isClockLike(a) ? 0 : 1;
            int cb = isClockLike(b) ? 0 : 1;
            return ca != cb ? ca - cb : collator.compare(label(a), label(b));
        });
        List<AppWidgetProviderInfo> items = new ArrayList<>();
        items.add(null); // built-in clock
        items.addAll(providers);

        new AlertDialog.Builder(this)
                .setTitle(R.string.glance_clock_title)
                .setAdapter(new Adapter(items), (dialog, which) -> {
                    AppWidgetProviderInfo info = items.get(which);
                    if (info == null) {
                        save(AppWidgetManager.INVALID_APPWIDGET_ID);
                    } else {
                        bind(info);
                    }
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    private boolean isClockLike(AppWidgetProviderInfo info) {
        String text = (label(info) + " " + info.provider.flattenToString())
                .toLowerCase(Locale.ROOT);
        return text.contains("clock") || text.contains("時計") || text.contains("時刻");
    }

    private String label(AppWidgetProviderInfo info) {
        CharSequence label = info.loadLabel(getPackageManager());
        return label == null ? "" : label.toString();
    }

    private void bind(AppWidgetProviderInfo info) {
        mPendingId = mHost.allocateAppWidgetId();
        mPendingInfo = info;
        if (mManager.bindAppWidgetIdIfAllowed(mPendingId, info.getProfile(), info.provider,
                null)) {
            configure();
        } else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                            info.getProfile());
            startActivityForResult(intent, REQUEST_BIND);
        }
    }

    private void configure() {
        AppWidgetProviderInfo info = mPendingInfo != null ? mPendingInfo
                : mManager.getAppWidgetInfo(mPendingId);
        if (info != null && info.configure != null
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)
                == 0) {
            try {
                mHost.startAppWidgetConfigureActivityForResult(this, mPendingId, 0,
                        REQUEST_CONFIGURE, null);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "Unable to configure widget", e);
            }
        }
        save(mPendingId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            mHost.deleteAppWidgetId(mPendingId);
            finish();
        } else if (requestCode == REQUEST_BIND) {
            configure();
        } else {
            save(mPendingId);
        }
    }

    private void save(int widgetId) {
        int old = CustomSettings.getGlanceClockWidgetId(this);
        if (old != AppWidgetManager.INVALID_APPWIDGET_ID && old != widgetId) {
            mHost.deleteAppWidgetId(old);
        }
        CustomSettings.setGlanceClockWidgetId(this, widgetId);
        finish();
    }

    private class Adapter extends BaseAdapter {
        private final List<AppWidgetProviderInfo> mItems;

        Adapter(List<AppWidgetProviderInfo> items) {
            mItems = items;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(
                    ClockWidgetPickerActivity.this).inflate(
                    R.layout.clock_widget_picker_item, parent, false);
            AppWidgetProviderInfo info = mItems.get(position);
            ImageView icon = view.findViewById(R.id.clock_widget_icon);
            TextView title = view.findViewById(R.id.clock_widget_label);
            TextView app = view.findViewById(R.id.clock_widget_app);
            if (info == null) {
                icon.setImageResource(R.drawable.ic_setting);
                title.setText(R.string.glance_clock_builtin);
                app.setVisibility(View.GONE);
            } else {
                icon.setImageDrawable(info.loadIcon(ClockWidgetPickerActivity.this,
                        getResources().getDisplayMetrics().densityDpi));
                title.setText(label(info));
                CharSequence appName;
                try {
                    appName = getPackageManager().getApplicationLabel(getPackageManager()
                            .getApplicationInfo(info.provider.getPackageName(), 0));
                } catch (Exception e) {
                    appName = info.provider.getPackageName();
                }
                app.setText(appName);
                app.setVisibility(View.VISIBLE);
            }
            return view;
        }
    }
}
