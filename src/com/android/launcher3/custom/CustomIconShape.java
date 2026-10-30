package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.PathParser;

import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.shapes.AppShape;
import com.android.launcher3.shapes.AppShapesProvider;

/**
 * User-selected mask shape for adaptive icons (Home settings → Icons → Icon shape).
 * Icons are re-wrapped in a {@link ShapedAdaptiveIconDrawable} before being baked to bitmaps.
 */
public final class CustomIconShape {

    private static final String TAG = "CustomIconShape";
    /** Viewport size of the AppShapesProvider SVG paths. */
    private static final float VIEWPORT = 100f;

    private static String sCachedKey;
    private static Path sCachedPath;

    private CustomIconShape() { }

    /** Returns the selected shape key, or "" when the system shape is used. */
    public static String getShapeKey(Context context) {
        String key = CustomSettings.getIconShape(context);
        return getShapePath(key) == null ? "" : key;
    }

    /** Returns the 100x100 mask path for the selected shape, or null for the system shape. */
    @Nullable
    public static Path getMaskPath(Context context) {
        return getShapePath(CustomSettings.getIconShape(context));
    }

    @Nullable
    private static synchronized Path getShapePath(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        if (key.equals(sCachedKey)) {
            return sCachedPath;
        }
        Path path = null;
        for (AppShape shape : AppShapesProvider.allShapes) {
            if (shape.getKey().equals(key)) {
                try {
                    path = PathParser.createPathFromPathData(shape.getPath().trim());
                } catch (RuntimeException e) {
                    Log.e(TAG, "Invalid shape path: " + key, e);
                }
                break;
            }
        }
        sCachedKey = key;
        sCachedPath = path;
        return path;
    }

    /**
     * Returns {@code icon} masked with the selected shape, or {@code icon} itself when the system
     * shape is selected. Extender icons (animated clock) keep their own drawing.
     */
    public static AdaptiveIconDrawable wrap(Context context, @Nullable AdaptiveIconDrawable icon) {
        if (icon == null || icon instanceof ShapedAdaptiveIconDrawable
                || icon instanceof BitmapInfo.Extender) {
            return icon;
        }
        Path mask = getMaskPath(context);
        if (mask == null) {
            return icon;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? new ShapedAdaptiveIconDrawable(icon.getBackground(), icon.getForeground(),
                        icon.getMonochrome(), mask)
                : new ShapedAdaptiveIconDrawable(icon.getBackground(), icon.getForeground(), mask);
    }

    /** Adaptive icon that clips its layers with a custom path instead of the system mask. */
    public static class ShapedAdaptiveIconDrawable extends AdaptiveIconDrawable {

        private final Path mBaseMask;
        private final Path mMaskScaleOnly = new Path();
        private final Path mMask = new Path();
        private final Matrix mMatrix = new Matrix();
        private final Paint mPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Canvas mLayerCanvas = new Canvas();

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        ShapedAdaptiveIconDrawable(Drawable background, Drawable foreground, Drawable monochrome,
                Path mask) {
            super(background, foreground, monochrome);
            mBaseMask = mask;
            updateMask(getBounds());
        }

        ShapedAdaptiveIconDrawable(Drawable background, Drawable foreground, Path mask) {
            super(background, foreground);
            mBaseMask = mask;
            updateMask(getBounds());
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            updateMask(bounds);
        }

        private void updateMask(Rect bounds) {
            if (mBaseMask == null) {
                return; // called from the super constructor
            }
            mMatrix.setScale(bounds.width() / VIEWPORT, bounds.height() / VIEWPORT);
            mBaseMask.transform(mMatrix, mMaskScaleOnly);
            mMask.set(mMaskScaleOnly);
            mMask.offset(bounds.left, bounds.top);
        }

        @Override
        public Path getIconMask() {
            return mMask;
        }

        @Override
        public void getOutline(@NonNull Outline outline) {
            outline.setPath(mMask);
        }

        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            mPaint.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int w = bounds.width();
            int h = bounds.height();
            if (w <= 0 || h <= 0) {
                return;
            }
            // Layers are laid out from (0, 0) by AdaptiveIconDrawable (b/211896569).
            Bitmap layers = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            layers.eraseColor(Color.BLACK);
            mLayerCanvas.setBitmap(layers);
            Drawable bg = getBackground();
            if (bg != null) {
                bg.draw(mLayerCanvas);
            }
            Drawable fg = getForeground();
            if (fg != null) {
                fg.draw(mLayerCanvas);
            }
            mLayerCanvas.setBitmap(null);
            mPaint.setShader(new BitmapShader(layers, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP));

            int count = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.drawPath(mMaskScaleOnly, mPaint);
            canvas.restoreToCount(count);
            mPaint.setShader(null);
        }
    }
}
