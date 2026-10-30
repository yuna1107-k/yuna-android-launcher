package com.android.launcher3.custom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayTouchProxy;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;

/**
 * Shows Google Discover on the screen to the left of the home screen, the same way Pixel and
 * Xperia Home do: the launcher binds to the Google app's overlay service and hands it the
 * launcher window, then the Google app draws the feed in a window attached to the launcher and
 * follows the scroll progress the launcher sends.
 *
 * The Google app may refuse launchers it doesn't trust; in that case no status is reported and
 * the home screen just behaves as if there were no left screen.
 */
public class DiscoverOverlay implements LauncherOverlayManager, LauncherOverlayTouchProxy {

    private static final String TAG = "DiscoverOverlay";

    static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String ACTION_OVERLAY = "com.android.launcher3.WINDOW_OVERLAY";

    // Client options sent with windowAttached: bit 0 = overlay (feed) enabled.
    private static final int CLIENT_OPTIONS = 1;
    // Activity state flags for setActivityState.
    private static final int STATE_STARTED = 1;
    private static final int STATE_RESUMED = 2;

    /** How far the home screen moves along with the feed, as a fraction of the width. */
    private static final float HOME_PARALLAX = 0.3f;

    private final Launcher mLauncher;
    private final boolean mIsRtl;
    private final OverlayCallback mCallback = new OverlayCallback(this);

    @Nullable private OverlayProxy mOverlay;
    @Nullable private LauncherOverlayCallbacks mLauncherCallbacks;
    @Nullable private WindowManager.LayoutParams mWindowAttrs;
    private boolean mBound;
    private boolean mOverlayInstalled;
    private boolean mHomeShifted;
    private int mActivityState;
    private int mApiVersion = 1;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mOverlay = new OverlayProxy(service);
            attachWindow();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mOverlay = null;
            setOverlayInstalled(false);
        }
    };

    public DiscoverOverlay(Launcher launcher) {
        mLauncher = launcher;
        mIsRtl = Utilities.isRtl(launcher.getResources());
    }

    // ---------------------------------------------------------------------------------------
    // Connection

    private Intent serviceIntent() {
        Uri data = Uri.parse("app://" + mLauncher.getPackageName() + ":" + Process.myUid())
                .buildUpon()
                .appendQueryParameter("v", "7")
                .appendQueryParameter("cv", "9")
                .build();
        return new Intent(ACTION_OVERLAY).setPackage(GOOGLE_PACKAGE).setData(data);
    }

    /** Connects or disconnects depending on the Home settings switch. */
    private void refreshConnection() {
        boolean enabled = CustomSettings.isDiscoverEnabled(mLauncher);
        if (enabled && !mBound) {
            Intent intent = serviceIntent();
            ResolveInfo info = mLauncher.getPackageManager()
                    .resolveService(intent, PackageManager.GET_META_DATA);
            if (info == null) {
                return;
            }
            mApiVersion = info.serviceInfo.metaData != null
                    ? info.serviceInfo.metaData.getInt("service.api.version", 1) : 1;
            try {
                mBound = mLauncher.bindService(intent, mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
            } catch (SecurityException e) {
                Log.w(TAG, "Not allowed to bind to the Google app", e);
            }
        } else if (!enabled && mBound) {
            disconnect(false);
        }
    }

    private void disconnect(boolean changingConfig) {
        if (mOverlay != null) {
            mOverlay.windowDetached(changingConfig);
        }
        if (mBound) {
            mLauncher.unbindService(mConnection);
            mBound = false;
        }
        mOverlay = null;
        setOverlayInstalled(false);
    }

    private void attachWindow() {
        if (mOverlay == null || mWindowAttrs == null) {
            return;
        }
        if (mApiVersion < 3) {
            mOverlay.windowAttached(mWindowAttrs, mCallback, CLIENT_OPTIONS);
        } else {
            Bundle bundle = new Bundle();
            bundle.putParcelable("layout_params", mWindowAttrs);
            bundle.putParcelable("configuration", mLauncher.getResources().getConfiguration());
            bundle.putInt("client_options", CLIENT_OPTIONS);
            mOverlay.windowAttached2(bundle, mCallback);
        }
        sendActivityState();
    }

    private void sendActivityState() {
        if (mOverlay == null) {
            return;
        }
        if (mApiVersion >= 4) {
            mOverlay.setActivityState(mActivityState);
        } else if ((mActivityState & STATE_RESUMED) != 0) {
            mOverlay.onResume();
        } else {
            mOverlay.onPause();
        }
    }

    private void setOverlayInstalled(boolean installed) {
        if (installed == mOverlayInstalled) {
            return;
        }
        mOverlayInstalled = installed;
        mLauncher.setLauncherOverlay(installed ? this : null);
        if (!installed) {
            onOverlayScrolled(0);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Callbacks from the Google app (main thread)

    void onOverlayStatusChanged(int status) {
        // Bit 0: the overlay is available and will draw the feed.
        setOverlayInstalled((status & 1) != 0);
    }

    void onOverlayScrolled(float progress) {
        progress = Utilities.boundToRange(progress, 0f, 1f);
        if (progress > 0 || mHomeShifted) {
            mHomeShifted = progress > 0;
            float shift = (mIsRtl ? -1 : 1) * mLauncher.getDragLayer().getWidth()
                    * HOME_PARALLAX * progress;
            for (View v : new View[] {mLauncher.getWorkspace(), mLauncher.getHotseat()}) {
                if (v != null) {
                    v.setTranslationX(shift);
                }
            }
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onOverlayScrollChanged(progress);
        }
    }

    // ---------------------------------------------------------------------------------------
    // LauncherOverlayTouchProxy: finger movement from the workspace edge

    @Override
    public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        mLauncherCallbacks = callbacks;
    }

    @Override
    public void onFlingVelocity(float velocity) { }

    @Override
    public void onOverlayMotionEvent(MotionEvent ev, float scrollProgress) {
        if (mOverlay == null) {
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> mOverlay.startScroll();
            case MotionEvent.ACTION_MOVE -> mOverlay.onScroll(Math.min(1f, scrollProgress));
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mOverlay.endScroll();
        }
    }

    // ---------------------------------------------------------------------------------------
    // LauncherOverlayManager: launcher lifecycle

    @Override
    public void onAttachedToWindow() {
        mWindowAttrs = mLauncher.getWindow().getAttributes();
        refreshConnection();
        attachWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        if (mOverlay != null) {
            mOverlay.windowDetached(mLauncher.isChangingConfigurations());
        }
        mWindowAttrs = null;
    }

    @Override
    public void onActivityStarted() {
        mActivityState |= STATE_STARTED;
        sendActivityState();
    }

    @Override
    public void onActivityResumed() {
        mActivityState |= STATE_RESUMED;
        refreshConnection();
        sendActivityState();
    }

    @Override
    public void onActivityPaused() {
        mActivityState &= ~STATE_RESUMED;
        sendActivityState();
    }

    @Override
    public void onActivityStopped() {
        mActivityState &= ~STATE_STARTED;
        sendActivityState();
    }

    @Override
    public void onActivityDestroyed() {
        disconnect(mLauncher.isChangingConfigurations());
    }

    @Override
    public void openOverlay() {
        if (mOverlay != null) {
            mOverlay.openOverlay(1 /* animate */);
        }
    }

    @Override
    public void hideOverlay(int duration) {
        if (mOverlay != null) {
            // Options: bit 0 = animate, remaining bits = duration.
            mOverlay.closeOverlay(duration > 0 ? (duration << 2) | 1 : 0);
        }
    }

    @Override
    public void dump(String prefix, PrintWriter w) {
        w.println(prefix + "DiscoverOverlay: bound=" + mBound + " connected=" + (mOverlay != null)
                + " installed=" + mOverlayInstalled + " apiVersion=" + mApiVersion);
    }

    // ---------------------------------------------------------------------------------------
    // Binder plumbing for the Google app's launcher overlay interfaces. Written by hand (rather
    // than with AIDL) so the code builds the same way under Soong and Gradle.

    /** Client for com.google.android.libraries.launcherclient.ILauncherOverlay. */
    private static class OverlayProxy {
        private static final String DESCRIPTOR =
                "com.google.android.libraries.launcherclient.ILauncherOverlay";
        private static final int START_SCROLL = IBinder.FIRST_CALL_TRANSACTION;
        private static final int ON_SCROLL = IBinder.FIRST_CALL_TRANSACTION + 1;
        private static final int END_SCROLL = IBinder.FIRST_CALL_TRANSACTION + 2;
        private static final int WINDOW_ATTACHED = IBinder.FIRST_CALL_TRANSACTION + 3;
        private static final int WINDOW_DETACHED = IBinder.FIRST_CALL_TRANSACTION + 4;
        private static final int CLOSE_OVERLAY = IBinder.FIRST_CALL_TRANSACTION + 5;
        private static final int ON_PAUSE = IBinder.FIRST_CALL_TRANSACTION + 6;
        private static final int ON_RESUME = IBinder.FIRST_CALL_TRANSACTION + 7;
        private static final int OPEN_OVERLAY = IBinder.FIRST_CALL_TRANSACTION + 8;
        private static final int WINDOW_ATTACHED_2 = IBinder.FIRST_CALL_TRANSACTION + 13;
        private static final int SET_ACTIVITY_STATE = IBinder.FIRST_CALL_TRANSACTION + 15;

        private final IBinder mRemote;

        OverlayProxy(IBinder remote) {
            mRemote = remote;
        }

        void startScroll() {
            call(START_SCROLL, null);
        }

        void onScroll(float progress) {
            call(ON_SCROLL, p -> p.writeFloat(progress));
        }

        void endScroll() {
            call(END_SCROLL, null);
        }

        void windowAttached(WindowManager.LayoutParams attrs, IBinder callback, int options) {
            call(WINDOW_ATTACHED, p -> {
                writeParcelable(p, attrs);
                p.writeStrongBinder(callback);
                p.writeInt(options);
            });
        }

        void windowAttached2(Bundle bundle, IBinder callback) {
            call(WINDOW_ATTACHED_2, p -> {
                writeParcelable(p, bundle);
                p.writeStrongBinder(callback);
            });
        }

        void windowDetached(boolean isChangingConfigurations) {
            call(WINDOW_DETACHED, p -> p.writeInt(isChangingConfigurations ? 1 : 0));
        }

        void closeOverlay(int options) {
            call(CLOSE_OVERLAY, p -> p.writeInt(options));
        }

        void openOverlay(int options) {
            call(OPEN_OVERLAY, p -> p.writeInt(options));
        }

        void onPause() {
            call(ON_PAUSE, null);
        }

        void onResume() {
            call(ON_RESUME, null);
        }

        void setActivityState(int state) {
            call(SET_ACTIVITY_STATE, p -> p.writeInt(state));
        }

        private static void writeParcelable(Parcel p, android.os.Parcelable value) {
            p.writeInt(1);
            value.writeToParcel(p, 0);
        }

        private interface Writer {
            void write(Parcel p);
        }

        /** All calls are one-way. */
        private void call(int code, @Nullable Writer writer) {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                if (writer != null) {
                    writer.write(data);
                }
                mRemote.transact(code, data, null, IBinder.FLAG_ONEWAY);
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Overlay call " + code + " failed", e);
            } finally {
                data.recycle();
            }
        }
    }

    /** Implements com.google.android.libraries.launcherclient.ILauncherOverlayCallback. */
    private static class OverlayCallback extends Binder {
        private static final String DESCRIPTOR =
                "com.google.android.libraries.launcherclient.ILauncherOverlayCallback";
        private static final int OVERLAY_SCROLL_CHANGED = IBinder.FIRST_CALL_TRANSACTION;
        private static final int OVERLAY_STATUS_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1;

        // Weak so a cached binder in the Google app doesn't keep the activity alive.
        private final WeakReference<DiscoverOverlay> mClient;

        OverlayCallback(DiscoverOverlay client) {
            mClient = new WeakReference<>(client);
            attachInterface(null, DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, @Nullable Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(DESCRIPTOR);
                }
                return true;
            }
            if (code != OVERLAY_SCROLL_CHANGED && code != OVERLAY_STATUS_CHANGED) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(DESCRIPTOR);
            DiscoverOverlay client = mClient.get();
            if (client == null) {
                return true;
            }
            if (code == OVERLAY_SCROLL_CHANGED) {
                float progress = data.readFloat();
                client.mLauncher.runOnUiThread(() -> client.onOverlayScrolled(progress));
            } else {
                int status = data.readInt();
                client.mLauncher.runOnUiThread(() -> client.onOverlayStatusChanged(status));
            }
            return true;
        }
    }
}
