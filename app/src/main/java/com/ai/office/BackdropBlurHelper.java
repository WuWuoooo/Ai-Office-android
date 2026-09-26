package com.ai.office;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * 真·局部模糊：
 *   从「屏幕背景 Bitmap」里裁切目标 View 的屏幕区域 → 高斯模糊 → 
 *   生成 LiquidGlassDrawable，作为该 View 的玻璃背景。
 *
 * 屏幕背景 Bitmap 由 MainActivity 在 applyChatBackground 里 setScreenBackground()。
 */
public class BackdropBlurHelper {

    private static volatile Bitmap sScreenBg = null;

    public static void setScreenBackground(Bitmap b) { sScreenBg = b; }
    public static Bitmap getScreenBackground() { return sScreenBg; }

    /** 给 View 生成局部模糊玻璃背景；失败返回 null（调用方回退到纯色 LiquidGlassDrawable） */
    public static Drawable makeForView(Context ctx, View view, float cornerRadius,
                                       int overlayAlpha, int highlightColor,
                                       int strokeColor, int shadowColor) {
        try {
            Bitmap bg = sScreenBg;
            if (bg == null || bg.isRecycled()) return null;
            if (view == null) return null;
            int w = view.getWidth();
            int h = view.getHeight();
            if (w <= 0 || h <= 0) return null;

            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            int x = loc[0], y = loc[1];

            int srcW = bg.getWidth(), srcH = bg.getHeight();
            if (x < 0) { w += x; x = 0; }
            if (y < 0) { h += y; y = 0; }
            if (x + w > srcW) w = srcW - x;
            if (y + h > srcH) h = srcH - y;
            if (w <= 0 || h <= 0) return null;

            Bitmap slice;
            try {
                slice = Bitmap.createBitmap(bg, x, y, w, h);
            } catch (Throwable t) { return null; }

            Bitmap blurred = GlassBackdropHelper.fastBlur(slice, 20);
            if (blurred != slice) { try { slice.recycle(); } catch (Throwable t) {} }
            if (blurred == null) return null;

            int fill = (overlayAlpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(cornerRadius, blurred, fill,
                    highlightColor, strokeColor, shadowColor);
        } catch (Throwable t) {
            return null;
        }
    }
}