package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Base64;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class UiUtils {

    public static final String PREFS = "ai_office_config";

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int sp(Context c, float v) {
        try {
            float scaledDensity = c.getResources().getDisplayMetrics().scaledDensity;
            return (int) (v * scaledDensity + 0.5f);
        } catch (Throwable t) {
            return (int) v;
        }
    }

    public static void toast(Context c, String msg) {
        try { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable t) {}
    }

    public static void toastLong(Context c, String msg) {
        try { Toast.makeText(c, msg, Toast.LENGTH_LONG).show(); } catch (Throwable t) {}
    }

    /** 安全取色：资源不存在时返回黑色，不抛异常 */
    public static int color(Context c, int resId) {
        try { return c.getResources().getColor(resId); } catch (Throwable t) { return 0xFF000000; }
    }

    /** 当前是否处于系统深色模式 */
    public static boolean isNight(Context c) {
        try {
            int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) { return false; }
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getInt(Context c, String key, int def) {
        try {
            String v = prefs(c).getString(key, String.valueOf(def));
            if (v == null || v.trim().length() == 0) return def;
            return Integer.parseInt(v.trim());
        } catch (Throwable t) { return def; }
    }

    public static boolean getBool(Context c, String key, boolean def) {
        try { return prefs(c).getBoolean(key, def); } catch (Throwable t) { return def; }
    }

    public static String getStr(Context c, String key, String def) {
        try {
            String v = prefs(c).getString(key, def);
            return v == null ? def : v;
        } catch (Throwable t) { return def; }
    }

    // ============================================================
    // 字体缩放（跟随系统 / 手动倍率）
    // ============================================================

    public static float fontScaleSetting(Context c) {
        try {
            String v = prefs(c).getString("font_scale", "0");
            if (v == null || v.trim().length() == 0) return 0f;
            return Float.parseFloat(v.trim());
        } catch (Throwable t) { return 0f; }
    }

    /** 仅按字体缩放包装 Context（旧接口，保留兼容） */
    public static Context applyFontScale(Context base) {
        try {
            float scale = fontScaleSetting(base);
            if (scale <= 0f) return base;
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.fontScale = scale;
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) { return base; }
    }

    /**
     * 同时应用「主题（浅色/深色/跟随系统）」和「字体缩放」到 Configuration。
     * 用户选浅色时，即使系统是深色，资源也会走 values/ 而不是 values-night/。
     */
    public static Context applyThemeAndFont(Context base) {
        if (base == null) return base;
        try {
            SharedPreferences p = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String themeStyle = p.getString("theme_style", "system");
            float scale = fontScaleSetting(base);

            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            boolean changed = false;

            if (scale > 0f) {
                cfg.fontScale = scale;
                changed = true;
            }

            if ("light".equals(themeStyle)) {
                cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
                changed = true;
            } else if ("dark".equals(themeStyle)) {
                cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
                changed = true;
            }

            if (!changed) return base;
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) { return base; }
    }

    public static String formatSize(long bytes) {
        try {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
            return String.format("%.1f MB", bytes / (1024f * 1024f));
        } catch (Throwable t) { return bytes + " B"; }
    }

    // ============================================================
    // JSON 辅助
    // ============================================================

    public static String optStr(JSONObject o, String key) {
        if (o == null || key == null) return "";
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof String) return (String) v;
        return String.valueOf(v);
    }

    public static String contentToText(JSONObject m, boolean withImageTag) {
        if (m == null) return "";
        Object c = m.opt("content");
        if (c == null || c == JSONObject.NULL) return "";
        if (c instanceof String) return (String) c;
        if (c instanceof JSONArray) {
            JSONArray arr = (JSONArray) c;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part == null) continue;
                String type = optStr(part, "type");
                if ("text".equals(type)) {
                    String t = optStr(part, "text");
                    if (t.length() > 0) {
                        if (sb.length() > 0) sb.append((char) 10);
                        sb.append(t);
                    }
                } else if ("image_url".equals(type)) {
                    if (!withImageTag) continue;
                    if (sb.length() > 0) sb.append((char) 10);
                    sb.append("[图片]");
                }
            }
            return sb.toString();
        }
        return String.valueOf(c);
    }

    public static String extractImageBase64(JSONObject m) {
        if (m == null) return null;
        Object c = m.opt("content");
        if (!(c instanceof JSONArray)) return null;
        JSONArray arr = (JSONArray) c;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            if (!"image_url".equals(optStr(p, "type"))) continue;
            JSONObject u = p.optJSONObject("image_url");
            if (u == null) continue;
            String url = optStr(u, "url");
            int k = url.indexOf("base64,");
            if (k >= 0) return url.substring(k + 7);
        }
        return null;
    }

    /** 提取消息里全部图片的 base64（支持多图消息），至少返回空列表 */
    public static List<String> extractImageBase64List(JSONObject m) {
        List<String> out = new ArrayList<String>();
        if (m == null) return out;
        Object c = m.opt("content");
        if (!(c instanceof JSONArray)) return out;
        JSONArray arr = (JSONArray) c;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            if (!"image_url".equals(optStr(p, "type"))) continue;
            JSONObject u = p.optJSONObject("image_url");
            if (u == null) continue;
            String url = optStr(u, "url");
            int k = url.indexOf("base64,");
            if (k >= 0) out.add(url.substring(k + 7));
        }
        return out;
    }

    /** 提取消息里全部图片的 url 原文（可能是 data:base64，也可能是 ref:本地文件路径），至少返回空列表 */
    public static List<String> extractImageUrls(JSONObject m) {
        List<String> out = new ArrayList<String>();
        if (m == null) return out;
        Object c = m.opt("content");
        if (!(c instanceof JSONArray)) return out;
        JSONArray arr = (JSONArray) c;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            if (!"image_url".equals(optStr(p, "type"))) continue;
            JSONObject u = p.optJSONObject("image_url");
            if (u == null) continue;
            String url = optStr(u, "url");
            if (url.length() > 0) out.add(url);
        }
        return out;
    }

    /** ref: 前缀的图片返回本地文件路径；否则返回 null */
    public static String refImagePath(String url) {
        if (url == null) return null;
        if (url.startsWith("ref:")) return url.substring(4);
        return null;
    }

    /** 从消息里解析出第一张可用的图片 base64：data: 直接取；ref: 读本地文件转码 */
    public static String resolveImageBase64(String url) {
        if (url == null || url.length() == 0) return null;
        String path = refImagePath(url);
        if (path != null) {
            try {
                File f = new File(path);
                if (!f.exists()) return null;
                FileInputStream fis = new FileInputStream(f);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                fis.close();
                return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            } catch (Throwable t) { return null; }
        }
        int k = url.indexOf("base64,");
        return k >= 0 ? url.substring(k + 7) : null;
    }

    // ============================================================
    // SharedPreferences 写入（commit 同步落盘）
    // ============================================================

    public static void putStr(Context c, String key, String v) {
        try {
            if (v == null || v.length() == 0) prefs(c).edit().remove(key).commit();
            else prefs(c).edit().putString(key, v).commit();
        } catch (Throwable t) {}
    }

    public static void putBool(Context c, String key, boolean v) {
        try { prefs(c).edit().putBoolean(key, v).commit(); } catch (Throwable t) {}
    }
}