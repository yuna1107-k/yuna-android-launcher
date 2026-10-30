/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.custom.CustomIconShape;
import com.android.launcher3.custom.IconPacks;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.icons.mono.MonoIconThemeController;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.UserIconInfo;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wrapper class to provide access to {@link BaseIconFactory} and also to provide pool of this class
 * that are threadsafe.
 */
public class LauncherIcons extends BaseIconFactory implements AutoCloseable {

    private static final MainThreadInitializedObject<Pool> POOL =
            new MainThreadInitializedObject<>(Pool::new);

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static LauncherIcons obtain(Context context) {
        return POOL.get(context).obtain();
    }

    public static void clearPool(Context context) {
        POOL.get(context).close();
    }

    private final ConcurrentLinkedQueue<LauncherIcons> mPool;
    private boolean mNormalizerPrimed;
    private ColorExtractor mColorExtractor;

    protected LauncherIcons(Context context, int fillResIconDpi, int iconBitmapSize,
            ConcurrentLinkedQueue<LauncherIcons> pool) {
        super(context, fillResIconDpi, iconBitmapSize,
                IconShape.INSTANCE.get(context).getShape().enableShapeDetection());
        if (Themes.isThemedIconEnabled(context)) {
            mThemeController = new MonoIconThemeController();
        }
        mPool = pool;
    }

    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    public void recycle() {
        clear();
        mPool.add(this);
    }

    // Custom: apply the user-selected icon shape (CustomIconShape).
    @Nullable
    @Override
    protected AdaptiveIconDrawable normalizeAndWrapToAdaptiveIcon(@Nullable Drawable icon,
            @Nullable RectF outIconBounds, @NonNull float[] outScale) {
        return CustomIconShape.wrap(mContext,
                super.normalizeAndWrapToAdaptiveIcon(icon, outIconBounds, outScale));
    }

    // Custom: the normalizer caches the adaptive icon scale, so compute it with the custom shape.
    @NonNull
    @Override
    public IconNormalizer getNormalizer() {
        IconNormalizer normalizer = super.getNormalizer();
        if (!mNormalizerPrimed) {
            mNormalizerPrimed = true;
            normalizer.getScale(CustomIconShape.wrap(mContext,
                    new AdaptiveIconDrawable(new ColorDrawable(Color.BLACK), null)),
                    null, null, null);
        }
        return normalizer;
    }

    // Custom: non-adaptive icon pack icons are drawn as-is (no adaptive wrapping/mask).
    @NonNull
    @Override
    public BitmapInfo createBadgedIconBitmap(@NonNull Drawable icon,
            @Nullable IconOptions options) {
        if (!(icon instanceof IconPacks.IconPackDrawable packIcon)) {
            return super.createBadgedIconBitmap(icon, options);
        }
        Drawable d = packIcon.getDrawable();
        float scale = getNormalizer().getScale(d, null, null, null);
        Bitmap bitmap = createIconBitmap(d, scale,
                options == null ? MODE_WITH_SHADOW : options.mGenerationMode);
        if (mColorExtractor == null) {
            mColorExtractor = new ColorExtractor();
        }
        int color = (options != null && options.mExtractedColor != null)
                ? options.mExtractedColor : mColorExtractor.findDominantColorByHue(bitmap);
        return BitmapInfo.of(bitmap, color).withFlags(getBitmapFlagOp(options));
    }

    @NonNull
    @Override
    protected UserIconInfo getUserInfo(@NonNull UserHandle user) {
        return UserCache.INSTANCE.get(mContext).getUserInfo(user);
    }

    @Override
    public void close() {
        recycle();
    }

    private static class Pool implements SafeCloseable {

        private final Context mContext;

        @NonNull
        private ConcurrentLinkedQueue<LauncherIcons> mPool = new ConcurrentLinkedQueue<>();

        private Pool(Context context) {
            mContext = context;
        }

        public LauncherIcons obtain() {
            ConcurrentLinkedQueue<LauncherIcons> pool = mPool;
            LauncherIcons m = pool.poll();

            if (m == null) {
                InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mContext);
                return new LauncherIcons(mContext, idp.fillResIconDpi, idp.iconBitmapSize, pool);
            } else {
                return m;
            }
        }

        @Override
        public void close() {
            mPool = new ConcurrentLinkedQueue<>();
        }
    }
}
