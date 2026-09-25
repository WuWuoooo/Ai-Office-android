package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * UI 覆盖层：集中管理可被 UI 插件覆盖的颜色与尺寸，并提供动态 drawable。
 *
 * 优先级：prefs 里的覆盖值 > 默认值（默认值通常来自 colors.xml 或硬编码）
 *
 * 白名单键：
 *   颜色：
 *     ui_color_primary          主色（按钮、链接等）
 *     ui_color_on_primary       主色上的文字
 *     ui_color_surface          表面背景
 *     ui_color_on_surface       主文字色
 *     ui_color_outline          次要文字 / 边框
 *     ui_color_error            错误色
 *     ui_color_divider          分割线
 *     ui_color_bubble_user_bg   用户气泡背景
 *     ui_color_bubble_user_text 用户气泡文字
 *     ui_color_bubble_ai_bg     AI 消息气泡背景
 *     ui_color_bubble_ai_text   AI 消息文字
 *     ui_color_code_bg          代码块 / 工具面板背景
 *     ui_color_code_text        代码文字
 *     ui_color_edit_bg          输入框背景
 *     ui_color_edit_border      输入框描边
 *     ui_color_topbar_bg        顶栏背景
 *     ui_color_input_bar_bg     底部输入栏背景
 *     ui_color_send_btn_bg      发送按钮背景
 *     ui_color_send_btn_text    发送按钮文字
 *     ui_color_card_bg          卡片背景
 *     ui_color_drawer_bg        侧边抽屉背景
 *   尺寸：
 *     ui_size_text             正文文字大小（sp）
 *     ui_size_title            标题文字大小（sp）
 *     ui_size_bubble_radius    气泡圆角（dp）
 *     ui_size_card_radius      卡片圆角（dp）
 *     ui_size_button_radius    按钮圆角（dp）
 */
public class UiOverrides {

    public static final String PREFS = "ai_office_config";

    /** 所有白名单键（顺序无所谓，插件只能写这些） */
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
            "ui_chrome_alpha"
            };

    // ============================================================
    // 通用读取
    // ============================================================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 该 key 是否在白名单 */
    public static boolean isWhitelisted(String key) {
        if (key == null) return false;
        for (int i = 0; i < ALL_KEYS.length; i++) {
            if (ALL_KEYS[i].equals(key)) return true;
        }
        return false;
    }

    /** 校验颜色：#RRGGBB 或 #AARRGGBB；空串视为"清除"也合法 */
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

    /** 校验尺寸：正整数；空串视为"清除"也合法 */
    public static boolean isValidSize(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() == 0) return true;
        try {
            int v = Integer.parseInt(s);
            return v > 0 && v <= 200;
        } catch (Throwable t) { return false; }
    }

/** 校验 chrome alpha：0-100 整数；空串 = 清除也合法 */
public static boolean isValidAlpha(String s) {
    if (s == null) return false;
    s = s.trim();
    if (s.length() == 0) return true;
    try {
        int v = Integer.parseInt(s);
        return v >= 0 && v <= 100;
    } catch (Throwable t) { return false; }
}

    /** 解析颜色；无效或空返回 fallback */
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
    // 颜色读取（带默认值）
    // ============================================================

    public static int color(Context c, String key, int fallback) {
        try {
            String v = prefs(c).getString(key, "");
            if (v == null || v.trim().length() == 0) return fallback;
            return parseColor(v, fallback);
        } catch (Throwable t) { return fallback; }
    }

    public static int primary(Context c) {
        return color(c, "ui_color_primary", UiUtils.color(c, R.color.md_primary));
    }

    public static int onPrimary(Context c) {
        return color(c, "ui_color_on_primary", UiUtils.color(c, R.color.md_on_primary));
    }

    public static int surface(Context c) {
        return color(c, "ui_color_surface", UiUtils.color(c, R.color.md_surface));
    }

    public static int onSurface(Context c) {
        return color(c, "ui_color_on_surface", UiUtils.color(c, R.color.md_on_surface));
    }

    public static int outline(Context c) {
        return color(c, "ui_color_outline", UiUtils.color(c, R.color.md_outline));
    }

    public static int error(Context c) {
        return color(c, "ui_color_error", UiUtils.color(c, R.color.md_error));
    }

    public static int divider(Context c) {
        return color(c, "ui_color_divider", UiUtils.color(c, R.color.md_divider));
    }

    /** 用户气泡背景。默认从 colors.xml 的 md_bubble_user 取。 */
    public static int bubbleUserBg(Context c) {
        return color(c, "ui_color_bubble_user_bg", UiUtils.color(c, R.color.md_bubble_user));
    }

    public static int bubbleUserText(Context c) {
        return color(c, "ui_color_bubble_user_text", UiUtils.color(c, R.color.md_on_surface));
    }

    /** AI 消息背景。默认透明（AI 消息本来没气泡背景）。 */
    public static int bubbleAiBg(Context c) {
        return color(c, "ui_color_bubble_ai_bg", 0x00000000);
    }

    public static int bubbleAiText(Context c) {
        return color(c, "ui_color_bubble_ai_text", UiUtils.color(c, R.color.md_on_surface));
    }

    public static int codeBg(Context c) {
        return color(c, "ui_color_code_bg", UiUtils.color(c, R.color.md_code_bg));
    }

    public static int codeText(Context c) {
        return color(c, "ui_color_code_text", UiUtils.color(c, R.color.md_code_text));
    }

    public static int editBg(Context c) {
        return color(c, "ui_color_edit_bg", UiUtils.color(c, R.color.md_edit_bg));
    }

    public static int editBorder(Context c) {
        return color(c, "ui_color_edit_border", UiUtils.color(c, R.color.md_edit_border));
    }

    public static int topbarBg(Context c) {
        return color(c, "ui_color_topbar_bg", UiUtils.color(c, R.color.md_surface));
    }

    public static int inputBarBg(Context c) {
        return color(c, "ui_color_input_bar_bg", UiUtils.color(c, R.color.md_surface));
    }

    public static int sendBtnBg(Context c) {
        return color(c, "ui_color_send_btn_bg", UiUtils.color(c, R.color.md_primary));
    }

    public static int sendBtnText(Context c) {
        return color(c, "ui_color_send_btn_text", UiUtils.color(c, R.color.md_on_primary));
    }

    public static int cardBg(Context c) {
        return color(c, "ui_color_card_bg", UiUtils.color(c, R.color.md_surface_variant));
    }

    public static int drawerBg(Context c) {
        return color(c, "ui_color_drawer_bg", UiUtils.color(c, R.color.md_surface));
    }

    // ============================================================
    // 尺寸读取（返回 px）
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

    /** 写入一个覆盖值；key 必须在白名单里；value 空串 = 清除；返回 true 表示写入成功 */
    public static boolean set(Context c, String key, String value) {
        try {
            if (!isWhitelisted(key)) return false;
            SharedPreferences.Editor ed = prefs(c).edit();
            if (value == null || value.trim().length() == 0) {
                ed.remove(key);
            } else {
                ed.putString(key, value.trim());
            }
            ed.commit();
            return true;
        } catch (Throwable t) { return false; }
    }

    /** 清除所有 UI 覆盖 */
    public static void clearAll(Context c) {
        try {
            SharedPreferences.Editor ed = prefs(c).edit();
            for (int i = 0; i < ALL_KEYS.length; i++) ed.remove(ALL_KEYS[i]);
            ed.commit();
        } catch (Throwable t) {}
    }

/**
 * 顶栏 / 输入栏背景的不透明度（0-255）。
 * 未设置时：有背景图 → 0（完全透明，透出背景图）；无背景图 → 255（不透明）。
 */
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

    /** 当前被覆盖的键数量 */
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

    /** 列出当前所有被覆盖的键（用于设置页展示） */
    public static String listOverrides(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            SharedPreferences p = prefs(c);
            for (int i = 0; i < ALL_KEYS.length; i++) {
                String v = p.getString(ALL_KEYS[i], "");
                if (v != null && v.trim().length() > 0) {
                    sb.append(ALL_KEYS[i]).append(" = ").append(v).append("\n");
                }
            }
            return sb.length() == 0 ? "（暂无覆盖）" : sb.toString();
        } catch (Throwable t) { return ""; }
    }

    // ============================================================
    // 动态背景：让插件改颜色后，XML shape 背景也能跟着变
    // 每种形状 = 纯色 + 圆角 + （可选）描边
    // ============================================================

    /** 生成一个带圆角的纯色 drawable */
    private static android.graphics.drawable.GradientDrawable rounded(Context c, int color, int radiusPx) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** 带描边的圆角 */
    private static android.graphics.drawable.GradientDrawable roundedStroked(Context c, int fill, int strokeColor, int radiusPx, int strokePx) {
        android.graphics.drawable.GradientDrawable d = rounded(c, fill, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** 用户气泡背景（圆角 + 纯色），对应 drawable/bubble_user_bg */
    public static android.graphics.drawable.Drawable bubbleUserBgDrawable(Context c) {
        return rounded(c, bubbleUserBg(c), bubbleRadius(c));
    }

    /** AI 消息背景；如果没设置覆盖色（默认透明）就返回 null，调用方不设置背景 */
    public static android.graphics.drawable.Drawable bubbleAiBgDrawable(Context c) {
        int color = bubbleAiBg(c);
        if ((color >>> 24) == 0) return null;   // alpha = 0 → 不设背景
        return rounded(c, color, bubbleRadius(c));
    }

    /** 代码块 / 工具面板背景（带描边） */
    public static android.graphics.drawable.Drawable codeBgDrawable(Context c) {
        return roundedStroked(c, codeBg(c), editBorder(c), UiUtils.dp(c, 10), UiUtils.dp(c, 1));
    }

    /** 卡片背景（圆角纯色） */
    public static android.graphics.drawable.Drawable cardBgDrawable(Context c) {
        return rounded(c, cardBg(c), cardRadius(c));
    }

    /** 输入框背景（圆角 + 描边） */
    public static android.graphics.drawable.Drawable editBgDrawable(Context c) {
        return roundedStroked(c, editBg(c), editBorder(c), UiUtils.dp(c, 24), UiUtils.dp(c, 1));
    }

    /** 发送按钮背景（圆角纯色） */
    public static android.graphics.drawable.Drawable sendBtnBgDrawable(Context c) {
        return rounded(c, sendBtnBg(c), buttonRadius(c));
    }

/** 顶栏背景（矩形纯色，带 chromeAlpha 透明度） */
public static android.graphics.drawable.Drawable topbarBgDrawable(Context c) {
    int color = applyAlpha(topbarBg(c), chromeAlpha(c));
    return rounded(c, color, 0);
}

/** 底部输入栏背景（矩形纯色，带 chromeAlpha 透明度） */
public static android.graphics.drawable.Drawable inputBarBgDrawable(Context c) {
    int color = applyAlpha(inputBarBg(c), chromeAlpha(c));
    return rounded(c, color, 0);
}

    /** 侧边抽屉背景（矩形纯色） */
    public static android.graphics.drawable.Drawable drawerBgDrawable(Context c) {
        return rounded(c, drawerBg(c), 0);
    }
}