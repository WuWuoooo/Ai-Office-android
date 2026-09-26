package com.ai.office;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Liquid Glass：纯 Canvas 绘制的液态玻璃质感 Drawable。
 * 不依赖屏幕背景模糊，直接通过多层渐变 + 描边 + 阴影模拟玻璃质感。
 *
 * 层次（从下到上）：
 *   1. 半透明填充底色
 *   2. 顶部主高光（white → transparent）
 *   3. 左上斜向反光条
 *   4. 右下暗角（模拟光源方向）
 *   5. 内侧白色描边（模拟玻璃边缘折射）
 *   6. 底部内侧阴影（厚度感）
 *   7. 左上内侧亮边（弧形高光）
 *
 * 构造函数保留了 Bitmap 参数的重载以便兼容旧调用方，但 Bitmap 参数已被忽略。
 */
public class LiquidGlassDrawable extends Drawable {

    private final float cornerRadius;
    private final int fillColor;
    private final int highlightColor;
    private final int strokeColor;
    private final int shadowColor;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkCornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    /** 无 Bitmap 版本 */
    public LiquidGlassDrawable(float cornerRadius, int fillColor,
                               int highlightColor, int strokeColor, int shadowColor) {
        this.cornerRadius = cornerRadius < 0 ? 0 : cornerRadius;
        this.fillColor = fillColor;
        this.highlightColor = highlightColor;
        this.strokeColor = strokeColor;
        this.shadowColor = shadowColor;
        initPaints();
    }

    /** 带 Bitmap 版本（兼容旧调用方，Bitmap 参数已被忽略） */
    public LiquidGlassDrawable(float cornerRadius, Bitmap content,
                               int fillColor, int highlightColor, int strokeColor, int shadowColor) {
        this(cornerRadius, fillColor, highlightColor, strokeColor, shadowColor);
    }

    private void initPaints() {
        fillPaint.setStyle(Paint.Style.FILL);
        topHighlightPaint.setStyle(Paint.Style.FILL);
        glossPaint.setStyle(Paint.Style.FILL);
        darkCornerPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(2f);
        innerGlowPaint.setStyle(Paint.Style.STROKE);
        innerGlowPaint.setStrokeWidth(3f);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        rect.set(b.left, b.top, b.right, b.bottom);
        float r = cornerRadius;
        float w = b.width();
        float h = b.height();

        // 1) 半透明填充底色
        fillPaint.setShader(null);
        fillPaint.setColor(fillColor);
        canvas.drawRoundRect(rect, r, r, fillPaint);

        // 用 clip 把后续效果限定在圆角内
        clipPath.reset();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        int save = canvas.save();
        try {
            canvas.clipPath(clipPath);
        } catch (Throwable t) {}

        // 2) 顶部主高光渐变
        if (Color.alpha(highlightColor) > 0) {
            float hEnd = b.top + Math.min(h * 0.55f, Math.max(r * 2f, 40f));
            topHighlightPaint.setShader(new LinearGradient(
                    0, b.top, 0, hEnd,
                    highlightColor, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, topHighlightPaint);
            topHighlightPaint.setShader(null);
        }

        // 3) 左上斜向反光条
        try {
            glossPaint.setShader(new LinearGradient(
                    b.left + w * 0.05f, b.top - h * 0.10f,
                    b.left + w * 0.65f, b.top + h * 0.75f,
                    0x3AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, glossPaint);
            glossPaint.setShader(null);
        } catch (Throwable t) {}

        // 4) 右下暗角
        try {
            RadialGradient rg = new RadialGradient(
                    b.right - w * 0.15f, b.bottom - h * 0.10f,
                    Math.max(w, h) * 0.9f,
                    0x00000000, 0x18000000, Shader.TileMode.CLAMP);
            darkCornerPaint.setShader(rg);
            canvas.drawRect(rect, darkCornerPaint);
            darkCornerPaint.setShader(null);
        } catch (Throwable t) {}

        canvas.restoreToCount(save);

        // 5) 内侧白色描边
        if (Color.alpha(strokeColor) > 0) {
            strokePaint.setColor(strokeColor);
            RectF inner = new RectF(rect);
            inner.inset(0.75f, 0.75f);
            canvas.drawRoundRect(inner, r, r, strokePaint);
        }

        // 6) 底部内侧阴影（厚度）
        if (Color.alpha(shadowColor) > 0) {
            shadowPaint.setColor(shadowColor);
            RectF inner = new RectF(rect);
            inner.inset(1f, 1f);
            int s2 = canvas.save();
            try {
                canvas.clipRect(rect.left, rect.centerY(), rect.right, rect.bottom);
                canvas.drawRoundRect(inner, r, r, shadowPaint);
            } catch (Throwable t) {}
            canvas.restoreToCount(s2);
        }

        // 7) 左上内侧亮边
        try {
            innerGlowPaint.setColor(0x60FFFFFF);
            RectF inner = new RectF(rect);
            inner.inset(1.5f, 1.5f);
            int s3 = canvas.save();
            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + h * 0.5f);
            canvas.drawRoundRect(inner, r, r, innerGlowPaint);
            canvas.restoreToCount(s3);
        } catch (Throwable t) {}
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 0; }
    @Override public int getIntrinsicHeight() { return 0; }
}