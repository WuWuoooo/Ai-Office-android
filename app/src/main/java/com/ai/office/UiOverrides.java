package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;

/**
 * UI 覆盖层：集中管理可被 UI 插件覆盖的颜色与尺寸，并提供动态 drawable。
 * 优先级：prefs 里的覆盖值 > 默认值。
 *
 * Liquid Glass：
 *   - 无 view 参数版本：纯 Canvas 液态玻璃（LiquidGlassDrawable）
 *   - 带 view 参数版本：直接转发无 view 版，兼容旧调用方
 */
public class UiOverrides {

    public static final String PREFS = "ai_office_config";

    public static final String[] ALL_KEYS = {
            "ui_color_primary", "ui_color_on_primary", "ui_color_surface", "ui_color_on_surface",
            "ui_color_outline", "ui_color_error", "ui_color_divider",
            "ui_color_bubble_user_bg", "ui_color_bubble_user_text",
            "ui_color_bubble_ai_bg", "ui_color_bubble_ai_text",
            "ui_color_code_bg", "ui_color_code_text",
            "ui_color_edit_bg", "ui_color_edit_border",
            "ui_color_topbar_bg", "ui_color_input_bar_bg",
            "ui_color_send_btn_bg", "ui_color_send_btn_text",
            "ui_color_card_bg", "ui_color_drawer_bg",
            "ui_size_text", "ui_size_title",
            "ui_size_bubble_radius", "ui_size_card_radius", "ui_size_button_radius",
            "ui_chrome_alpha",
            "ui_glass_enabled", "ui_glass_alpha"
    };

    // ============================================================
    // 通用读取
    // ============================================================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isWhitelisted(String key) {
        if (key == null) return false;
        for (int i = 0; i < ALL_KEYS.length; i++) {
            if (ALL_KEYS[i].equals(key)) return true;
        }
        return false;
    }

    public static boolean isValidColor(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() == 0) return true;
        if (s.charAt(0) != '#') return false;
        if (s.length() != 7 && s.length() != 9) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    public static boolean isValidSize(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() == 0) return true;
        try {
            int v = Integer.parseInt(s);
            return v > 0 && v <= 200;
        } catch (Throwable t) { return false; }
    }

    public static boolean isValidAlpha(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() == 0) return true;
        try {
            int v = Integer.parseInt(s);
            return v >= 0 && v <= 100;
        } catch (Throwable t) { return false; }
    }

    public static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.length() == 0) return fallback;
        try {
            if (s.length() == 7) return Color.parseColor(s);
            if (s.length() == 9) {
                long v = Long.parseLong(s.substring(1), 16);
                return (int) v;
            }
        } catch (Throwable t) {}
        return fallback;
    }

    // ============================================================
    // 颜色读取
    // ============================================================

    public static int color(Context c, String key, int fallback) {
        try {
            String v = prefs(c).getString(key, "");
            if (v == null || v.trim().length() == 0) return fallback;
            return parseColor(v, fallback);
        } catch (Throwable t) { return fallback; }
    }

    public static int primary(Context c) { return color(c, "ui_color_primary", UiUtils.color(c, R.color.md_primary)); }
    public static int onPrimary(Context c) { return color(c, "ui_color_on_primary", UiUtils.color(c, R.color.md_on_primary)); }
    public static int surface(Context c) { return color(c, "ui_color_surface", UiUtils.color(c, R.color.md_surface)); }
    public static int onSurface(Context c) { return color(c, "ui_color_on_surface", UiUtils.color(c, R.color.md_on_surface)); }
    public static int outline(Context c) { return color(c, "ui_color_outline", UiUtils.color(c, R.color.md_outline)); }
    public static int error(Context c) { return color(c, "ui_color_error", UiUtils.color(c, R.color.md_error)); }
    public static int divider(Context c) { return color(c, "ui_color_divider", UiUtils.color(c, R.color.md_divider)); }
    public static int bubbleUserBg(Context c) { return color(c, "ui_color_bubble_user_bg", UiUtils.color(c, R.color.md_bubble_user)); }
    public static int bubbleUserText(Context c) { return color(c, "ui_color_bubble_user_text", UiUtils.color(c, R.color.md_on_surface)); }
    public static int bubbleAiBg(Context c) { return color(c, "ui_color_bubble_ai_bg", 0x00000000); }
    public static int bubbleAiText(Context c) { return color(c, "ui_color_bubble_ai_text", UiUtils.color(c, R.color.md_on_surface)); }
    public static int codeBg(Context c) { return color(c, "ui_color_code_bg", UiUtils.color(c, R.color.md_code_bg)); }
    public static int codeText(Context c) { return color(c, "ui_color_code_text", UiUtils.color(c, R.color.md_code_text)); }
    public static int editBg(Context c) { return color(c, "ui_color_edit_bg", UiUtils.color(c, R.color.md_edit_bg)); }
    public static int editBorder(Context c) { return color(c, "ui_color_edit_border", UiUtils.color(c, R.color.md_edit_border)); }
    public static int topbarBg(Context c) { return color(c, "ui_color_topbar_bg", UiUtils.color(c, R.color.md_surface)); }
    public static int inputBarBg(Context c) { return color(c, "ui_color_input_bar_bg", UiUtils.color(c, R.color.md_surface)); }
    public static int sendBtnBg(Context c) { return color(c, "ui_color_send_btn_bg", UiUtils.color(c, R.color.md_primary)); }
    public static int sendBtnText(Context c) { return color(c, "ui_color_send_btn_text", UiUtils.color(c, R.color.md_on_primary)); }
    public static int cardBg(Context c) { return color(c, "ui_color_card_bg", UiUtils.color(c, R.color.md_surface_variant)); }
    public static int drawerBg(Context c) { return color(c, "ui_color_drawer_bg", UiUtils.color(c, R.color.md_surface)); }

    // ============================================================
    // 尺寸读取
    // ============================================================

    private static int readSp(Context c, String key, int defSp) {
        try {
            String v = prefs(c).getString(key, "");
            if (v == null || v.trim().length() == 0) return UiUtils.sp(c, defSp);
            int sp = Integer.parseInt(v.trim());
            if (sp <= 0 || sp > 200) return UiUtils.sp(c, defSp);
            return UiUtils.sp(c, sp);
        } catch (Throwable t) { return UiUtils.sp(c, defSp); }
    }

    private static int readDp(Context c, String key, int defDp) {
        try {
            String v = prefs(c).getString(key, "");
            if (v == null || v.trim().length() == 0) return UiUtils.dp(c, defDp);
            int dp = Integer.parseInt(v.trim());
            if (dp <= 0 || dp > 200) return UiUtils.dp(c, defDp);
            return UiUtils.dp(c, dp);
        } catch (Throwable t) { return UiUtils.dp(c, defDp); }
    }

    public static int textSize(Context c)      { return readSp(c, "ui_size_text", 15); }
    public static int titleSize(Context c)     { return readSp(c, "ui_size_title", 17); }
    public static int bubbleRadius(Context c)  { return readDp(c, "ui_size_bubble_radius", 16); }
    public static int cardRadius(Context c)    { return readDp(c, "ui_size_card_radius", 14); }
    public static int buttonRadius(Context c)  { return readDp(c, "ui_size_button_radius", 20); }

    // ============================================================
    // 管理
    // ============================================================

    public static boolean set(Context c, String key, String value) {
        try {
            if (!isWhitelisted(key)) return false;
            SharedPreferences.Editor ed = prefs(c).edit();
            if (value == null || value.trim().length() == 0) ed.remove(key);
            else ed.putString(key, value.trim());
            ed.commit();
            return true;
        } catch (Throwable t) { return false; }
    }

    public static void clearAll(Context c) {
        try {
            SharedPreferences.Editor ed = prefs(c).edit();
            for (int i = 0; i < ALL_KEYS.length; i++) ed.remove(ALL_KEYS[i]);
            ed.commit();
        } catch (Throwable t) {}
    }

    public static int chromeAlpha(Context c) {
        try {
            String v = prefs(c).getString("ui_chrome_alpha", "");
            if (v == null || v.trim().length() == 0) {
                boolean hasBg = UiUtils.getStr(c, "chat_bg_path", "").length() > 0;
                return hasBg ? 0 : 255;
            }
            int i = Integer.parseInt(v.trim());
            if (i < 0) i = 0;
            if (i > 100) i = 100;
            return (int) (i * 255f / 100f);
        } catch (Throwable t) { return 255; }
    }

    private static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int countOverrides(Context c) {
        try {
            int n = 0;
            SharedPreferences p = prefs(c);
            for (int i = 0; i < ALL_KEYS.length; i++) {
                String v = p.getString(ALL_KEYS[i], "");
                if (v != null && v.trim().length() > 0) n++;
            }
            return n;
        } catch (Throwable t) { return 0; }
    }

    public static String listOverrides(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            SharedPreferences p = prefs(c);
            for (int i = 0; i < ALL_KEYS.length; i++) {
                String v = p.getString(ALL_KEYS[i], "");
                if (v != null && v.trim().length() > 0) {
                    sb.append(ALL_KEYS[i]).append(" = ").append(v).append((char) 10);
                }
            }
            return sb.length() == 0 ? "（暂无覆盖）" : sb.toString();
        } catch (Throwable t) { return ""; }
    }

    // ============================================================
    // 玻璃模式
    // ============================================================

    public static boolean glassEnabled(Context c) {
        try { return "1".equals(UiUtils.getStr(c, "ui_glass_enabled", "")); }
        catch (Throwable t) { return false; }
    }

    public static int glassAlphaValue(Context c) {
        try {
            String v = UiUtils.getStr(c, "ui_glass_alpha", "140");
            if (v == null || v.trim().length() == 0) return 140;
            int i = Integer.parseInt(v.trim());
            return i < 0 ? 0 : (i > 255 ? 255 : i);
        } catch (Throwable t) { return 140; }
    }

    // ============================================================
    // 动态背景
    // ============================================================

    private static android.graphics.drawable.GradientDrawable rounded(Context c, int color, int radiusPx) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private static android.graphics.drawable.GradientDrawable roundedStroked(Context c, int fill, int strokeColor, int radiusPx, int strokePx) {
        android.graphics.drawable.GradientDrawable d = rounded(c, fill, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    // -------- 气泡 --------

    public static android.graphics.drawable.Drawable bubbleUserBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c);
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(bubbleRadius(c), fill, 0x9AFFFFFF, 0x66FFFFFF, 0x1A000000);
        }
        return rounded(c, bubbleUserBg(c), bubbleRadius(c));
    }

    public static android.graphics.drawable.Drawable bubbleAiBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c) * 3 / 4;
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(bubbleRadius(c), fill, 0x88FFFFFF, 0x55FFFFFF, 0x14000000);
        }
        int color = bubbleAiBg(c);
        if ((color >>> 24) == 0) return null;
        return rounded(c, color, bubbleRadius(c));
    }

    // -------- 代码块 / 工具面板 / 卡片 / 输入框 --------

    public static android.graphics.drawable.Drawable codeBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c) * 2 / 3;
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(UiUtils.dp(c, 10), fill, 0x80FFFFFF, 0x55FFFFFF, 0x12000000);
        }
        return roundedStroked(c, codeBg(c), editBorder(c), UiUtils.dp(c, 10), UiUtils.dp(c, 1));
    }

    public static android.graphics.drawable.Drawable cardBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c) * 3 / 4;
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(cardRadius(c), fill, 0x9AFFFFFF, 0x66FFFFFF, 0x18000000);
        }
        return rounded(c, cardBg(c), cardRadius(c));
    }

    public static android.graphics.drawable.Drawable editBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c) * 3 / 4;
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(UiUtils.dp(c, 24), fill, 0x80FFFFFF, 0x66FFFFFF, 0x10000000);
        }
        return roundedStroked(c, editBg(c), editBorder(c), UiUtils.dp(c, 24), UiUtils.dp(c, 1));
    }

    public static android.graphics.drawable.Drawable sendBtnBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = glassAlphaValue(c);
            int fill = (alpha << 24) | (sendBtnBg(c) & 0x00FFFFFF);
            return new LiquidGlassDrawable(buttonRadius(c), fill, 0x70FFFFFF, 0x50FFFFFF, 0x18000000);
        }
        return rounded(c, sendBtnBg(c), buttonRadius(c));
    }

    // -------- 顶栏 / 输入栏 / 抽屉 --------

    public static android.graphics.drawable.Drawable topbarBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = (int) (glassAlphaValue(c) * 0.6f);
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(0, fill, 0x48FFFFFF, 0x38FFFFFF, 0);
        }
        int color = applyAlpha(topbarBg(c), chromeAlpha(c));
        return rounded(c, color, 0);
    }

    public static android.graphics.drawable.Drawable inputBarBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = (int) (glassAlphaValue(c) * 0.6f);
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(0, fill, 0x30FFFFFF, 0x30FFFFFF, 0x15000000);
        }
        int color = applyAlpha(inputBarBg(c), chromeAlpha(c));
        return rounded(c, color, 0);
    }

    public static android.graphics.drawable.Drawable drawerBgDrawable(Context c) {
        if (glassEnabled(c)) {
            int alpha = (int) (glassAlphaValue(c) * 0.75f);
            int fill = (alpha << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(0, fill, 0x40FFFFFF, 0x38FFFFFF, 0);
        }
        return rounded(c, drawerBg(c), 0);
    }

    public static android.graphics.drawable.Drawable glassItemBgDrawable(Context c, boolean active) {
        int radius = UiUtils.dp(c, 10);
        int alpha = glassAlphaValue(c);
        if (active) {
            int fill = (Math.min(255, alpha + 30) << 24) | 0x00FFFFFF;
            return new LiquidGlassDrawable(radius, fill, 0x80FFFFFF, 0x50FFFFFF, 0x1A000000);
        }
        int fill = (alpha * 2 / 3) << 24 | 0x00FFFFFF;
        return new LiquidGlassDrawable(radius, fill, 0x40FFFFFF, 0x30FFFFFF, 0x10000000);
    }

    public static android.graphics.drawable.Drawable outlineBtnBgDrawable(Context c) {
        if (!glassEnabled(c)) return null;
        int alpha = glassAlphaValue(c) / 2;
        int fill = (alpha << 24) | 0x00FFFFFF;
        return new LiquidGlassDrawable(UiUtils.dp(c, 14), fill, 0, 0x50FFFFFF, 0);
    }

    // ============================================================
    // view-aware 重载：直接转发无 view 版（不再裁切背景）
    // ============================================================

    public static android.graphics.drawable.Drawable bubbleUserBgDrawable(Context c, View v) {
        return bubbleUserBgDrawable(c);
    }

    public static android.graphics.drawable.Drawable bubbleAiBgDrawable(Context c, View v) {
        return bubbleAiBgDrawable(c);
    }

    public static android.graphics.drawable.Drawable codeBgDrawable(Context c, View v) {
        return codeBgDrawable(c);
    }

    public static android.graphics.drawable.Drawable cardBgDrawable(Context c, View v) {
        return cardBgDrawable(c);
    }
}