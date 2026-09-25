package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 插件系统（两种形态）
 *
 * ── 形态 A：AI 工具插件 ─────────────────────────────
 *   类型：prompt / http / script，注册为 AI 可调用的函数工具
 *
 * ── 形态 B：UI 扩展插件 ─────────────────────────────
 *   类型："ui"，用于修改 App 界面功能。App 内置以下扩展点：
 *     · main_menu           顶栏「⋯」更多菜单
 *     · message_long_press  AI 消息长按菜单
 *     · input_plus          「+」附件菜单
 *     · settings_item       设置页「插件」二级页
 *     · toolbar             底部工具栏（预留）
 *
 * UI 插件的 action.kind 支持 4 种：
 *     · prompt     渲染模板文本 → 填入输入框 / 弹窗展示
 *     · http       发 HTTP 请求 → 响应作为用户消息 / 弹窗展示
 *     · script     执行 shell   → 输出作为用户消息 / 弹窗展示
 *     · ui_change  修改 App UI  → 键值对写入 UiOverrides（白名单约束）
 */
public class PluginManager {

    private static final int MAX_HTTP_BYTES = 512 * 1024;
    private static final int MAX_SCRIPT_BYTES = 256 * 1024;

    /** UI 插件扩展点常量 */
    public static final String EXT_MAIN_MENU = "main_menu";
    public static final String EXT_MESSAGE_LONG_PRESS = "message_long_press";
    public static final String EXT_INPUT_PLUS = "input_plus";
    public static final String EXT_SETTINGS_ITEM = "settings_item";
    public static final String EXT_TOOLBAR = "toolbar";

    public static File pluginDir() {
        File d = new File(Environment.getExternalStorageDirectory(), "AI/plugins");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ============================================================
    // 插件扫描
    // ============================================================

    private static List<JSONObject> loadAll(Context ctx) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            File dir = pluginDir();
            File[] fs = dir.listFiles();
            if (fs == null) return out;

            SharedPreferences p = ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
            String disabledRaw = p.getString("disabled_plugins", "");
            List<String> disabled = new ArrayList<String>();
            if (disabledRaw != null && disabledRaw.length() > 0) {
                String[] parts = disabledRaw.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String s = parts[i].trim();
                    if (s.length() > 0) disabled.add(s);
                }
            }

            for (int i = 0; i < fs.length; i++) {
                File f = fs[i];
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase();
                if (!n.endsWith(".json")) continue;
                try {
                    String txt = readFile(f, 400000);
                    if (txt == null) continue;
                    JSONObject o = new JSONObject(txt);
                    String name = o.optString("name", "").trim();
                    if (name.length() == 0) continue;
                    if (disabled.contains(name)) continue;

                    String type = o.optString("type", "prompt").trim().toLowerCase();
                    if (type.length() == 0) type = "prompt";

                    if ("script".equals(type)) {
                        if (!p.getBoolean("allow_script_plugins", false)) continue;
                    } else if ("ui".equals(type)) {
                        String ext = o.optString("extension", "").trim();
                        if (!isValidExtension(ext)) continue;
                    } else if (!"prompt".equals(type) && !"http".equals(type)) {
                        continue;
                    }
                    o.put("_type", type);
                    out.add(o);
                } catch (Throwable t) {
                    // 单文件失败忽略
                }
            }
        } catch (Throwable t) {}
        return out;
    }

    // ============================================================
    // 形态 A：AI 工具插件
    // ============================================================

    public static List<JSONObject> loadPlugins(Context ctx) {
        List<JSONObject> all = loadAll(ctx);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; i < all.size(); i++) {
            JSONObject o = all.get(i);
            String t = o.optString("_type", "prompt");
            if ("ui".equals(t)) continue;
            out.add(o);
        }
        return out;
    }

    public static void registerTools(Context ctx, JSONArray tools) {
        if (tools == null) return;
        try {
            List<JSONObject> plugins = loadPlugins(ctx);
            for (int i = 0; i < plugins.size(); i++) {
                JSONObject p = plugins.get(i);
                String name = p.optString("name", "");
                String desc = p.optString("description", "用户自定义插件");
                JSONObject params = p.optJSONObject("parameters");

                JSONObject tool = new JSONObject();
                tool.put("type", "function");
                JSONObject fn = new JSONObject();
                fn.put("name", "plugin_" + name);
                fn.put("description", desc);

                JSONObject schema = new JSONObject();
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONArray required = new JSONArray();
                if (params != null) {
                    Iterator<String> it = params.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject pv = params.optJSONObject(k);
                        if (pv != null) {
                            props.put(k, pv);
                            required.put(k);
                        }
                    }
                }
                schema.put("properties", props);
                if (required.length() > 0) schema.put("required", required);
                fn.put("parameters", schema);
                tool.put("function", fn);
                tools.put(tool);
            }
        } catch (Throwable t) {}
    }

    // ============================================================
    // 形态 B：UI 扩展插件
    // ============================================================

    public static List<JSONObject> loadUiPlugins(Context ctx) {
        List<JSONObject> all = loadAll(ctx);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; i < all.size(); i++) {
            JSONObject o = all.get(i);
            if ("ui".equals(o.optString("_type"))) out.add(o);
        }
        return out;
    }

    public static List<JSONObject> getUiPluginsByExtension(Context ctx, String extension) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (extension == null) return out;
        try {
            List<JSONObject> all = loadUiPlugins(ctx);
            for (int i = 0; i < all.size(); i++) {
                JSONObject p = all.get(i);
                if (extension.equals(p.optString("extension", ""))) out.add(p);
            }
        } catch (Throwable t) {}
        return out;
    }

    private static boolean isValidExtension(String ext) {
        if (ext == null) return false;
        return EXT_MAIN_MENU.equals(ext)
                || EXT_MESSAGE_LONG_PRESS.equals(ext)
                || EXT_INPUT_PLUS.equals(ext)
                || EXT_SETTINGS_ITEM.equals(ext)
                || EXT_TOOLBAR.equals(ext);
    }

    // ============================================================
    // UI 插件 action 执行
    // ============================================================

    public static String executeUiAction(Context ctx, JSONObject plugin, JSONObject args) {
        try {
            if (plugin == null) return "错误: 插件为空";
            JSONObject action = plugin.optJSONObject("action");
            if (action == null) return "错误: 插件未定义 action";
            String kind = action.optString("kind", "prompt").toLowerCase();
            if ("prompt".equals(kind)) {
                return renderTemplate(action.optString("template", ""), args);
            }
            if ("http".equals(kind)) {
                return execHttpAction(action, args);
            }
            if ("script".equals(kind)) {
                return execScriptAction(action, args);
            }
            if ("ui_change".equals(kind)) {
                return execUiChangeAction(ctx, action, args);
            }
            return "错误: 未知 action.kind = " + kind;
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        }
    }

    // ============================================================
    // ui_change：把插件的 changes 写入 UiOverrides（白名单约束）
    // ============================================================

    /**
     * 执行 ui_change：
     *   读 action.changes 里的 {key: value}，对 value 做模板替换后写入 prefs。
     *   只有 UiOverrides.isWhitelisted(key) 为 true 的键才被写入。
     *   空字符串 = 清除该键。
     *   颜色必须匹配 #RRGGBB / #AARRGGBB；尺寸必须为正整数。
     */
    private static String execUiChangeAction(Context ctx, JSONObject action, JSONObject args) {
        try {
            if (ctx == null) return "错误: context 为空";
            JSONObject changes = action.optJSONObject("changes");
            if (changes == null) return "错误: action.changes 未定义";

            SharedPreferences prefs = ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = prefs.edit();

            int applied = 0, cleared = 0, skipped = 0, invalid = 0;
            StringBuilder report = new StringBuilder();

            Iterator<String> it = changes.keys();
            while (it.hasNext()) {
                String key = it.next();
                if (!UiOverrides.isWhitelisted(key)) {
                    skipped++;
                    report.append("跳过（不在白名单）: ").append(key).append("\n");
                    continue;
                }
                String value = renderTemplate(changes.optString(key, ""), args);

                boolean isColorKey = key.startsWith("ui_color_");
boolean isSizeKey = key.startsWith("ui_size_");
boolean isAlphaKey = "ui_chrome_alpha".equals(key);

if (isColorKey && !UiOverrides.isValidColor(value)) {
    invalid++;
    report.append("忽略（颜色格式非法）: ").append(key).append(" = ").append(value).append("\n");
    continue;
}
if (isSizeKey && !UiOverrides.isValidSize(value)) {
    invalid++;
    report.append("忽略（尺寸格式非法）: ").append(key).append(" = ").append(value).append("\n");
    continue;
}
if (isAlphaKey && !UiOverrides.isValidAlpha(value)) {
    invalid++;
    report.append("忽略（透明度格式非法，应为 0-100）: ").append(key).append(" = ").append(value).append("\n");
    continue;
}

                if (value == null || value.trim().length() == 0) {
                    ed.remove(key);
                    cleared++;
                } else {
                    ed.putString(key, value.trim());
                    applied++;
                }
            }
            ed.commit();

            StringBuilder sb = new StringBuilder();
            sb.append("UI 覆盖已应用：写入 ").append(applied)
              .append(" 项，清除 ").append(cleared)
              .append(" 项，跳过 ").append(skipped)
              .append(" 项，格式错误 ").append(invalid).append(" 项。");
            if (report.length() > 0) sb.append("\n\n").append(report);
            return sb.toString();
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        }
    }

    // ============================================================
    // http / script action
    // ============================================================

    private static String execHttpAction(JSONObject action, JSONObject args) {
        HttpURLConnection conn = null;
        try {
            String method = action.optString("method", "GET").toUpperCase();
            String url = renderTemplate(action.optString("url", ""), args);
            String body = renderTemplate(action.optString("body", ""), args);
            JSONObject headers = action.optJSONObject("headers");

            if ("GET".equals(method) && body.length() > 0 && !url.contains("?")) {
                url = url + "?" + body;
            }

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "application/json, text/*");
            if (headers != null) {
                Iterator<String> it = headers.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    conn.setRequestProperty(k, headers.optString(k, ""));
                }
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                conn.setDoOutput(true);
                if (body.length() > 0 && !isFormLike(body)) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                } else if (body.length() > 0) {
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                }
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is, MAX_HTTP_BYTES);
            if (code < 200 || code >= 300) return "错误: HTTP " + code + "\n" + clip(resp, 2000);
            return clip(resp, 20000);
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private static String execScriptAction(JSONObject action, JSONObject args) {
        Process proc = null;
        try {
            String cmd = renderTemplate(action.optString("script", ""), args);
            if (cmd.trim().length() == 0) return "错误: script 为空";
            int timeoutSec = action.optInt("timeout_sec", 15);
            if (timeoutSec <= 0) timeoutSec = 15;
            if (timeoutSec > 60) timeoutSec = 60;

            proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            final Process fp = proc;
            final int ft = timeoutSec;
            Thread killer = new Thread(new Runnable() {
                @Override public void run() {
                    try { Thread.sleep(ft * 1000L); } catch (Throwable t) {}
                    try { fp.destroy(); } catch (Throwable t) {}
                }
            });
            killer.setDaemon(true);
            killer.start();

            String stdout = readAll(proc.getInputStream(), MAX_SCRIPT_BYTES);
            String stderr = readAll(proc.getErrorStream(), 64 * 1024);
            int exit = proc.waitFor();
            killer.interrupt();

            StringBuilder sb = new StringBuilder();
            sb.append("exit code: ").append(exit).append("\n");
            if (stdout.length() > 0) sb.append("--- stdout ---\n").append(clip(stdout, 20000)).append("\n");
            if (stderr.length() > 0) sb.append("--- stderr ---\n").append(clip(stderr, 4000)).append("\n");
            if (stdout.length() == 0 && stderr.length() == 0) sb.append("（无输出）");
            return sb.toString();
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        } finally {
            if (proc != null) try { proc.destroy(); } catch (Throwable t) {}
        }
    }

    // ============================================================
    // AI 工具插件执行（形态 A）
    // ============================================================

    public static String execute(Context ctx, String toolName, String argsJson) {
        try {
            if (toolName == null || !toolName.startsWith("plugin_")) return "不是插件工具";
            String pluginName = toolName.substring("plugin_".length());
            JSONObject args = new JSONObject(argsJson == null ? "{}" : argsJson);

            List<JSONObject> plugins = loadPlugins(ctx);
            for (int i = 0; i < plugins.size(); i++) {
                JSONObject p = plugins.get(i);
                if (!pluginName.equals(p.optString("name", ""))) continue;
                String type = p.optString("_type", "prompt");
                if ("prompt".equals(type)) return renderTemplate(p.optString("prompt", ""), args);
                if ("http".equals(type)) return execHttpAction(p, args);
                if ("script".equals(type)) return execScriptAction(p, args);
                return "未知插件类型: " + type;
            }
            return "未找到插件 " + pluginName + "（请在 /sdcard/AI/plugins/ 放入对应的 .json）";
        } catch (Throwable t) {
            return "插件执行失败: " + t.getMessage();
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private static String renderTemplate(String tpl, JSONObject args) {
        if (tpl == null || tpl.length() == 0) return "";
        String out = tpl;
        try {
            if (args != null) {
                Iterator<String> it = args.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object v = args.opt(k);
                    String sv = (v == null || v == JSONObject.NULL) ? "" : String.valueOf(v);
                    out = out.replace("{{" + k + "}}", sv);
                }
            }
        } catch (Throwable t) {}
        return out;
    }

    private static boolean isFormLike(String s) {
        if (s == null || s.length() == 0) return false;
        if (s.trim().startsWith("{") || s.trim().startsWith("[")) return false;
        return s.indexOf('=') > 0;
    }

    private static String readFile(File f, int maxBytes) {
        try {
            long len = f.length();
            if (len <= 0) return null;
            if (len > maxBytes) len = maxBytes;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) { int r = fis.read(buf, off, buf.length - off); if (r < 0) break; off += r; }
            fis.close();
            return new String(buf, 0, off, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    private static String readAll(InputStream is, int maxBytes) {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = is.read(buf)) > 0) {
                if (total + n > maxBytes) {
                    bos.write(buf, 0, maxBytes - total);
                    total = maxBytes;
                    break;
                }
                bos.write(buf, 0, n);
                total += n;
            }
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...(已截断)";
    }

    public static int countPlugins(Context ctx) {
        return loadPlugins(ctx).size();
    }

    public static int countUiPlugins(Context ctx) {
        return loadUiPlugins(ctx).size();
    }
}