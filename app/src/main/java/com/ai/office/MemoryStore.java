package com.ai.office;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 长期记忆：跨会话保存用户偏好、项目背景等。
 * 存储位置：filesDir/memory.json
 * 结构： { "key1": "内容1", "key2": "内容2" }
 */
public class MemoryStore {

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "memory.json");
    }

    private static synchronized JSONObject load(Context ctx) {
        try {
            File f = file(ctx);
            if (!f.exists()) return new JSONObject();
            long len = f.length();
            if (len <= 0) return new JSONObject();
            if (len > 5 * 1024 * 1024) len = 5 * 1024 * 1024;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) { int r = fis.read(buf, off, buf.length - off); if (r < 0) break; off += r; }
            fis.close();
            return new JSONObject(new String(buf, 0, off, "UTF-8"));
        } catch (Throwable t) { return new JSONObject(); }
    }

    private static synchronized void saveObj(Context ctx, JSONObject o) {
        try {
            FileOutputStream fos = new FileOutputStream(file(ctx));
            fos.write(o.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {}
    }

    public static synchronized String save(Context ctx, String key, String content) {
        try {
            if (key == null || key.trim().length() == 0) return "记忆 key 不能为空";
            if (content == null) content = "";
            if (content.length() > 20000) content = content.substring(0, 20000) + "\n...(已截断)";
            JSONObject o = load(ctx);
            o.put(key.trim(), content);
            saveObj(ctx, o);
            return "已保存记忆 [" + key.trim() + "]，共 " + content.length() + " 字符";
        } catch (Throwable t) { return "保存记忆失败: " + t.getMessage(); }
    }

    public static synchronized String read(Context ctx, String key) {
        try {
            if (key == null || key.trim().length() == 0) return "记忆 key 不能为空";
            JSONObject o = load(ctx);
            if (!o.has(key.trim())) return "没有找到记忆 [" + key.trim() + "]";
            return "记忆 [" + key.trim() + "]:\n" + o.optString(key.trim(), "");
        } catch (Throwable t) { return "读取记忆失败: " + t.getMessage(); }
    }

    /** 删除一条记忆；成功返回 true */
    public static synchronized boolean delete(Context ctx, String key) {
        try {
            if (key == null || key.trim().length() == 0) return false;
            JSONObject o = load(ctx);
            if (!o.has(key.trim())) return false;
            o.remove(key.trim());
            saveObj(ctx, o);
            return true;
        } catch (Throwable t) { return false; }
    }

    /** 清空所有记忆 */
    public static synchronized void clearAll(Context ctx) {
        try { saveObj(ctx, new JSONObject()); } catch (Throwable t) {}
    }

    public static synchronized String listAll(Context ctx) {
        try {
            JSONObject o = load(ctx);
            if (o.length() == 0) return "（目前还没有保存任何长期记忆）";
            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(o.length()).append(" 条长期记忆：\n\n");
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k, "");
                String preview = v.replace('\n', ' ');
                if (preview.length() > 80) preview = preview.substring(0, 80) + "…";
                sb.append("• ").append(k).append(": ").append(preview).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) { return "列出记忆失败: " + t.getMessage(); }
    }

    /** 返回所有记忆的 key 列表（设置页展示用） */
    public static synchronized List<String> getAllKeys(Context ctx) {
        List<String> out = new ArrayList<String>();
        try {
            JSONObject o = load(ctx);
            Iterator<String> it = o.keys();
            while (it.hasNext()) out.add(it.next());
        } catch (Throwable t) {}
        return out;
    }

    /** 返回所有记忆的 {key, content} 列表（设置页展示用） */
    public static synchronized List<String[]> getAllEntries(Context ctx) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            JSONObject o = load(ctx);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.add(new String[]{k, o.optString(k, "")});
            }
        } catch (Throwable t) {}
        return out;
    }

    /** 供 MainActivity 构建系统提示词时使用：把全部记忆拼成一段文本（没有则返回空串） */
    public static synchronized String buildMemoryBlock(Context ctx) {
        try {
            JSONObject o = load(ctx);
            if (o.length() == 0) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("【已知长期记忆】\n");
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k, "");
                if (v.length() > 2000) v = v.substring(0, 2000) + "…";
                sb.append("[").append(k).append("] ").append(v).append("\n");
            }
            sb.append("\n请参考以上长期记忆来理解用户的偏好与项目背景。\n");
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
}