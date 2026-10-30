package com.android.launcher3.custom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.android.launcher3.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Third-party icon packs (ADW / Nova / Lawnchair format: res/xml/appfilter.xml or
 * assets/appfilter.xml mapping {@code ComponentInfo{pkg/cls}} to drawable names).
 */
public final class IconPacks {

    private static final String TAG = "IconPacks";

    private static final String[] PACK_ACTIONS = {
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
    };

    private static final Object LOCK = new Object();
    private static IconPack sLoaded;
    private static String sLastState;

    private IconPacks() { }

    /** Returns installed icon packs as (label, package), sorted by label. */
    public static List<Pair<String, String>> queryInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, String> packs = new LinkedHashMap<>();
        for (String action : PACK_ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!packs.containsKey(pkg)) {
                    packs.put(pkg, String.valueOf(ri.activityInfo.applicationInfo.loadLabel(pm)));
                }
            }
        }
        List<Pair<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String> e : packs.entrySet()) {
            result.add(Pair.create(e.getValue(), e.getKey()));
        }
        Collator collator = Collator.getInstance();
        result.sort((a, b) -> collator.compare(a.first, b.first));
        return result;
    }

    /** Fills the icon pack ListPreference with "System" + the installed packs. */
    public static void setupPreference(ListPreference pref) {
        Context context = pref.getContext();
        List<Pair<String, String>> packs = queryInstalled(context);
        CharSequence[] entries = new CharSequence[packs.size() + 1];
        CharSequence[] values = new CharSequence[packs.size() + 1];
        entries[0] = context.getString(R.string.icon_pack_system);
        values[0] = "";
        for (int i = 0; i < packs.size(); i++) {
            entries[i + 1] = packs.get(i).first;
            values[i + 1] = packs.get(i).second;
        }
        pref.setEntries(entries);
        pref.setEntryValues(values);
        if (pref.getValue() == null || pref.findIndexOfValue(pref.getValue()) < 0) {
            pref.setValue(""); // selected pack was uninstalled
        }
    }

    /** Returns a string that changes when the selected pack (or its version) changes. */
    public static String getState(Context context) {
        String pkg = CustomSettings.getIconPack(context);
        String state = "";
        if (!TextUtils.isEmpty(pkg)) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
                state = pkg + "@" + info.lastUpdateTime;
            } catch (PackageManager.NameNotFoundException e) {
                // Uninstalled: behave as if no pack is selected.
            }
        }
        synchronized (LOCK) {
            if (!state.equals(sLastState)) {
                // Pack changed or updated: re-read its appfilter.
                sLoaded = null;
                sLastState = state;
            }
        }
        return state;
    }

    /** Returns the pack icon for an activity, or null if the pack has none. */
    @Nullable
    public static Drawable getIcon(Context context, ComponentInfo info, int iconDpi) {
        IconPack pack = getSelectedPack(context);
        return pack == null ? null
                : pack.getIcon(pack.mComponents.get(
                        new ComponentName(info.packageName, info.name)), iconDpi);
    }

    /** Returns the pack icon for an app (first entry for its package), or null. */
    @Nullable
    public static Drawable getIcon(Context context, ApplicationInfo info, int iconDpi) {
        IconPack pack = getSelectedPack(context);
        return pack == null ? null : pack.getIcon(pack.mPackages.get(info.packageName), iconDpi);
    }

    @Nullable
    private static IconPack getSelectedPack(Context context) {
        String pkg = CustomSettings.getIconPack(context);
        if (TextUtils.isEmpty(pkg)) {
            return null;
        }
        synchronized (LOCK) {
            if (sLoaded == null || !sLoaded.mPackage.equals(pkg)) {
                sLoaded = IconPack.load(context, pkg);
            }
            return sLoaded.mResources == null ? null : sLoaded;
        }
    }

    /**
     * Marks a non-adaptive pack icon so LauncherIcons draws it as-is instead of wrapping it
     * in an adaptive icon with a white background.
     */
    public static class IconPackDrawable extends DrawableWrapper {
        IconPackDrawable(Drawable dr) {
            super(dr);
        }
    }

    private static class IconPack {
        final String mPackage;
        @Nullable final Resources mResources;
        final Map<ComponentName, String> mComponents = new HashMap<>();
        final Map<String, String> mPackages = new HashMap<>();
        private final Map<String, Integer> mIds = new ConcurrentHashMap<>();

        private IconPack(String pkg, @Nullable Resources res) {
            mPackage = pkg;
            mResources = res;
        }

        static IconPack load(Context context, String pkg) {
            Resources res;
            try {
                res = context.getPackageManager().getResourcesForApplication(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Icon pack not installed: " + pkg);
                return new IconPack(pkg, null);
            }
            IconPack pack = new IconPack(pkg, res);
            try {
                pack.parseAppFilter();
            } catch (Exception e) {
                Log.e(TAG, "Unable to parse appfilter of " + pkg, e);
            }
            return pack;
        }

        private void parseAppFilter() throws Exception {
            XmlPullParser parser;
            InputStream in = null;
            int id = mResources.getIdentifier("appfilter", "xml", mPackage);
            if (id != 0) {
                parser = mResources.getXml(id);
            } else {
                in = mResources.getAssets().open("appfilter.xml");
                parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(in, null);
            }
            try {
                int type;
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG || !"item".equals(parser.getName())) {
                        continue;
                    }
                    String component = parser.getAttributeValue(null, "component");
                    String drawable = parser.getAttributeValue(null, "drawable");
                    ComponentName cn = parseComponent(component);
                    if (cn == null || TextUtils.isEmpty(drawable)) {
                        continue;
                    }
                    mComponents.putIfAbsent(cn, drawable);
                    mPackages.putIfAbsent(cn.getPackageName(), drawable);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
            Log.d(TAG, "Loaded " + mComponents.size() + " icons from " + mPackage);
        }

        @Nullable
        private static ComponentName parseComponent(@Nullable String s) {
            if (s == null) {
                return null;
            }
            s = s.trim();
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            return ComponentName.unflattenFromString(s.substring(start + 1, end).trim());
        }

        @Nullable
        Drawable getIcon(@Nullable String name, int iconDpi) {
            if (name == null || mResources == null) {
                return null;
            }
            int id = mIds.computeIfAbsent(name, this::findId);
            if (id == 0) {
                return null;
            }
            try {
                Drawable d = mResources.getDrawableForDensity(id, iconDpi, null);
                if (d == null) {
                    return null;
                }
                return d instanceof AdaptiveIconDrawable ? d : new IconPackDrawable(d);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }

        private int findId(@NonNull String name) {
            int id = mResources.getIdentifier(name, "drawable", mPackage);
            if (id == 0) {
                id = mResources.getIdentifier(name, "mipmap", mPackage);
            }
            return id;
        }
    }
}
