package com.android.launcher3.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Builds an icon in the selected icon pack's style for apps the pack doesn't cover:
 * 1. the pack's own fallback recipe (iconback / iconmask / iconupon / scale), if it has one;
 * 2. otherwise, for line-style packs (mostly transparent icons, e.g. Arcticons / Lawnicons),
 *    the app's monochrome (themed icon) layer in the pack's line color;
 * 3. otherwise the app's first letter in a thin circle, in the pack's line color;
 * for packs of adaptive icons (e.g. Lawnicons: a solid background with a line foreground) the
 * same line art goes on the pack's background color as an adaptive icon. Other packs with solid
 * icons and no recipe keep the app's own icon.
 */
final class IconPackFallback {

    private static final int SIZE = 192;

    /** What the pack's icons look like, sampled once from a few of its icons. */
    static final class Style {
        final boolean lineArt;
        final int color;
        /** Pack icons are adaptive: use {@link #background} behind {@link #color} line art. */
        final boolean adaptive;
        final int background;

        Style(boolean lineArt, int color, boolean adaptive, int background) {
            this.lineArt = lineArt;
            this.color = color;
            this.adaptive = adaptive;
            this.background = background;
        }
    }

    /** Average color of the opaque pixels of {@code d}, or 0 if it has none. */
    private static int averageColor(Drawable d, Bitmap bmp, Canvas canvas, int[] px) {
        bmp.eraseColor(Color.TRANSPARENT);
        d.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
        d.draw(canvas);
        bmp.getPixels(px, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
        long r = 0, g = 0, b = 0, n = 0;
        for (int p : px) {
            if (Color.alpha(p) > 128) {
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        return n == 0 ? 0 : Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    private IconPackFallback() { }

    /** Samples pack icons: mostly transparent → line art; their dominant opaque color. */
    static Style sample(Iterable<Drawable> icons) {
        long coverage = 0;
        long pixels = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        long colored = 0;
        Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int[] px = new int[64 * 64];
        int adaptiveCount = 0;
        int total = 0;
        int adaptiveBg = 0;
        int adaptiveFg = 0;
        for (Drawable d : icons) {
            total++;
            if (d instanceof AdaptiveIconDrawable aid && aid.getBackground() != null
                    && aid.getForeground() != null) {
                adaptiveCount++;
                if (adaptiveBg == 0) {
                    adaptiveBg = averageColor(aid.getBackground(), bmp, canvas, px);
                    adaptiveFg = averageColor(aid.getForeground(), bmp, canvas, px);
                }
            }
        }
        if (total > 0 && adaptiveCount * 2 > total && adaptiveBg != 0 && adaptiveFg != 0) {
            bmp.recycle();
            return new Style(false, adaptiveFg, true, adaptiveBg);
        }
        for (Drawable d : icons) {
            bmp.eraseColor(Color.TRANSPARENT);
            d.setBounds(0, 0, 64, 64);
            d.draw(canvas);
            bmp.getPixels(px, 0, 64, 0, 0, 64, 64);
            for (int p : px) {
                pixels++;
                int a = Color.alpha(p);
                if (a > 128) {
                    coverage++;
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                    colored++;
                }
            }
        }
        bmp.recycle();
        if (pixels == 0 || colored == 0) {
            return new Style(false, Color.WHITE, false, 0);
        }
        boolean lineArt = coverage < pixels * 0.35;
        int color = Color.rgb((int) (r / colored), (int) (g / colored), (int) (b / colored));
        return new Style(lineArt, color, false, 0);
    }

    /** Pack recipe: draw on iconback, mask with iconmask, overlay iconupon. */
    @Nullable
    static Drawable compose(Context context, Drawable original, @Nullable Drawable back,
            @Nullable Drawable mask, @Nullable Drawable upon, float scale) {
        if (back == null && mask == null && upon == null) {
            return null;
        }
        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (back != null) {
            back.setBounds(0, 0, SIZE, SIZE);
            back.draw(canvas);
        }
        int inset = Math.round(SIZE * (1 - scale) / 2);
        original.setBounds(inset, inset, SIZE - inset, SIZE - inset);
        original.draw(canvas);
        if (mask != null) {
            Bitmap maskBmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            mask.setBounds(0, 0, SIZE, SIZE);
            mask.draw(new Canvas(maskBmp));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvas.drawBitmap(maskBmp, 0, 0, p);
            maskBmp.recycle();
        }
        if (upon != null) {
            upon.setBounds(0, 0, SIZE, SIZE);
            upon.draw(canvas);
        }
        return new BitmapDrawable(context.getResources(), bmp);
    }

    /** Adaptive fallback: line art in the pack's colors, shaped like the other icons. */
    static Drawable adaptiveIcon(Context context, Drawable original, @Nullable CharSequence label,
            Style style) {
        Drawable fg = new android.graphics.drawable.InsetDrawable(
                lineIcon(context, original, label, style.color), 1 / 6f);
        return new AdaptiveIconDrawable(
                new android.graphics.drawable.ColorDrawable(style.background), fg);
    }

    /** Line-style fallback: the app's monochrome layer, else its first letter in a circle. */
    static Drawable lineIcon(Context context, Drawable original, @Nullable CharSequence label,
            int color) {
        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Drawable mono = original instanceof AdaptiveIconDrawable aid ? aid.getMonochrome() : null;
        if (mono != null) {
            // Adaptive layers are 108dp with the visible 72dp in the middle.
            int pad = SIZE / 4;
            mono = mono.mutate();
            mono.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            mono.setBounds(-pad, -pad, SIZE + pad, SIZE + pad);
            mono.draw(canvas);
        } else {
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(SIZE * 0.04f);
            ring.setColor(color);
            float r = SIZE * 0.32f;
            canvas.drawCircle(SIZE / 2f, SIZE / 2f, r, ring);

            String letter = TextUtils.isEmpty(label) ? "?"
                    : new String(Character.toChars(label.toString().codePointAt(0)));
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(color);
            text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            text.setTextSize(SIZE * 0.34f);
            Rect bounds = new Rect();
            text.getTextBounds(letter, 0, letter.length(), bounds);
            canvas.drawText(letter, SIZE / 2f - bounds.exactCenterX(),
                    SIZE / 2f - bounds.exactCenterY(), text);
        }
        return new BitmapDrawable(context.getResources(), bmp);
    }
}
