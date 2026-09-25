package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class FileToolExecutor {

    private static final int MAX_LIST_ENTRIES = 800;
    private static final int MAX_SEARCH_RESULTS = 100;
    private static final int MAX_SCAN_FILES = 20000;
    private static final int MAX_TREE_ENTRIES = 300;
    private static final int MAX_TREE_DEPTH = 8;

    private final File workspace;
    private final Context ctx;

    public FileToolExecutor(Context ctx) {
        this.ctx = ctx;
        workspace = new File(ctx.getFilesDir(), "workspace");
        if (!workspace.exists()) workspace.mkdirs();
    }

    public String execute(String toolName, String argsJson) {
        try {
            if (argsJson == null || argsJson.trim().length() == 0) argsJson = "{}";
            JSONObject args = new JSONObject(argsJson);
            return executeTool(toolName, args);
        } catch (Throwable e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    private String executeTool(String tool, JSONObject args) {
        try {
            if (tool == null) return "未知工具";

            if ("search_files".equals(tool)) return searchFiles(resolve(args.optString("path","")), args);
            if ("get_current_time".equals(tool)) return currentTime();
            if ("list_memory".equals(tool)) return MemoryStore.listAll(ctx);
            if ("save_memory".equals(tool)) return MemoryStore.save(ctx, args.optString("key",""), args.optString("content",""));
            if ("read_memory".equals(tool)) return MemoryStore.read(ctx, args.optString("key",""));

            String path = args.optString("path", "");
            File target = resolve(path);

            if ("list_files".equals(tool)) return listFiles(target);
            if ("list_dir_tree".equals(tool)) {
                int maxD = args.optInt("max_depth", 3);
                if (maxD <= 0) maxD = 3;
                if (maxD > MAX_TREE_DEPTH) maxD = MAX_TREE_DEPTH;
                int maxE = args.optInt("max_entries", MAX_TREE_ENTRIES);
                if (maxE <= 0) maxE = MAX_TREE_ENTRIES;
                StringBuilder sb = new StringBuilder();
                sb.append(target.getAbsolutePath()).append("\n");
                int[] cnt = new int[1];
                walkTree(target, "", 0, maxD, maxE, cnt, sb);
                if (cnt[0] >= maxE) sb.append("...(已达条目上限)\n");
                return sb.toString();
            }
            if ("file_info".equals(tool)) return fileInfo(target);
            if ("read_file".equals(tool)) return readFile(target);
            if ("read_file_range".equals(tool)) {
                return readRange(target, args.optInt("start_line", 1), args.optInt("end_line", 200));
            }
            if ("write_file".equals(tool)) return writeFile(target, optStr(args, "content"), false);
            if ("append_file".equals(tool)) return writeFile(target, optStr(args, "content"), true);
            if ("edit_file".equals(tool)) return editFile(target, args);
            if ("create_dir".equals(tool)) {
                if (target.exists()) return "目录已存在: " + target.getAbsolutePath();
                if (target.mkdirs()) return "已创建目录: " + target.getAbsolutePath();
                return "创建目录失败: " + target.getAbsolutePath();
            }
            if ("move_file".equals(tool)) return moveOrCopy(resolve(args.optString("src","")), resolve(args.optString("dst","")), args.optBoolean("overwrite", false), true);
            if ("copy_file".equals(tool)) return moveOrCopy(resolve(args.optString("src","")), resolve(args.optString("dst","")), args.optBoolean("overwrite", false), false);
            if ("delete_file".equals(tool)) return deleteFile(target, args);
            if ("rename_file".equals(tool)) return renameFile(resolve(args.optString("path","")), args.optString("new_name",""));

            return "未知工具: " + tool;
        } catch (Throwable e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    private String optStr(JSONObject args, String key) {
        Object o = args.opt(key);
        if (o == null || o == JSONObject.NULL) return "";
        return String.valueOf(o);
    }

    private File resolve(String path) throws IOException {
        if (path == null) path = "";
        path = path.trim();
        if (path.length() == 0) return workspace;
        if (path.startsWith("~")) return new File(ctx.getFilesDir(), path.substring(1));
        if (path.startsWith("/")) return new File(path);
        File f = new File(workspace, path);
        String cp = f.getCanonicalPath();
        String wp = workspace.getCanonicalPath();
        if (!cp.startsWith(wp)) throw new IOException("路径越界");
        return f;
    }

    private String currentTime() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE");
            String now = sdf.format(new Date());
            return "当前时间: " + now + "\n时间戳(ms): " + System.currentTimeMillis();
        } catch (Throwable t) { return "获取时间失败"; }
    }

    // ============================================================
    // 树形列目录
    // ============================================================

    private void walkTree(File dir, String prefix, int depth, int maxDepth, int maxEntries, int[] cnt, StringBuilder sb) {
        if (dir == null || depth > maxDepth) return;
        if (cnt[0] >= maxEntries) return;
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) return;

        List<File> dirs = new ArrayList<File>();
        List<File> files = new ArrayList<File>();
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) dirs.add(f); else files.add(f);
        }
        Collections.sort(dirs);
        Collections.sort(files);

        List<File> all = new ArrayList<File>();
        all.addAll(dirs);
        all.addAll(files);

        for (int i = 0; i < all.size(); i++) {
            if (cnt[0] >= maxEntries) return;
            File f = all.get(i);
            boolean last = (i == all.size() - 1);
            sb.append(prefix).append(last ? "└─ " : "├─ ");
            sb.append(f.getName());
            if (f.isFile()) sb.append(" (").append(f.length()).append("B)");
            sb.append("\n");
            cnt[0]++;
            if (f.isDirectory() && depth < maxDepth) {
                walkTree(f, prefix + (last ? "   " : "│  "), depth + 1, maxDepth, maxEntries, cnt, sb);
            }
        }
    }

    // ============================================================
    // 文件信息
    // ============================================================

    private String fileInfo(File f) {
        if (!f.exists()) return "不存在: " + f.getAbsolutePath();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder sb = new StringBuilder();
            sb.append("路径: ").append(f.getAbsolutePath()).append("\n");
            sb.append("类型: ").append(f.isDirectory() ? "目录" : "文件").append("\n");
            sb.append("大小: ").append(f.length()).append(" 字节\n");
            sb.append("可读: ").append(f.canRead()).append("  可写: ").append(f.canWrite()).append("\n");
            sb.append("修改时间: ").append(sdf.format(new Date(f.lastModified()))).append("\n");
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                sb.append("子项数: ").append(fs == null ? 0 : fs.length).append("\n");
            }
            return sb.toString();
        } catch (Throwable t) { return "读取信息失败: " + t.getMessage(); }
    }

    // ============================================================
    // 按行区间读文件
    // ============================================================

    private String readRange(File f, int start, int end) {
        if (!f.exists()) return "文件不存在: " + f.getAbsolutePath();
        if (f.isDirectory()) return "这是一个目录";
        if (start < 1) start = 1;
        if (end < start) end = start;
        if (end - start > 2000) end = start + 2000;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            int ln = 0;
            int shown = 0;
            while ((line = r.readLine()) != null) {
                ln++;
                if (ln < start) continue;
                if (ln > end) break;
                sb.append(ln).append(": ").append(line).append("\n");
                shown++;
            }
            r.close();
            if (shown == 0) return "区间内没有内容（文件总行数可能不足）";
            return "文件: " + f.getAbsolutePath() + " 行 " + start + "-" + end + "（共 " + shown + " 行）\n" + sb.toString();
        } catch (Throwable t) { return "读取失败: " + t.getMessage(); }
    }

    // ============================================================
    // list_files
    // ============================================================

    private String listFiles(File dir) {
        if (!dir.exists()) return "目录不存在: " + dir.getAbsolutePath();
        if (!dir.isDirectory()) return "不是目录: " + dir.getAbsolutePath();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "（空目录）" + dir.getAbsolutePath();
        StringBuilder sb = new StringBuilder();
        sb.append("目录: ").append(dir.getAbsolutePath()).append("\n");
        int count = 0;
        boolean truncated = false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (pass == 0 && !f.isDirectory()) continue;
                if (pass == 1 && f.isDirectory()) continue;
                if (count >= MAX_LIST_ENTRIES) { truncated = true; break; }
                sb.append(f.isDirectory() ? "[目录] " : "[文件] ");
                sb.append(f.getName());
                if (f.isFile()) sb.append(" (").append(f.length()).append(" bytes)");
                sb.append("\n");
                count++;
            }
            if (truncated) break;
        }
        if (truncated) {
            sb.append("...(条目过多，仅显示前 ").append(MAX_LIST_ENTRIES)
              .append(" 项，本目录共 ").append(files.length).append(" 项)\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 读/写/改/删
    // ============================================================

    private String readFile(File f) {
        if (!f.exists()) return "文件不存在: " + f.getAbsolutePath();
        if (f.isDirectory()) return "这是一个目录，请使用 list_files: " + f.getAbsolutePath();
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
            String maxSizeStr = prefs.getString("max_size_mb", "5");
            long maxSizeMb;
            try { maxSizeMb = Long.parseLong(maxSizeStr.trim()); } catch (Throwable t) { maxSizeMb = 5; }
            if (maxSizeMb <= 0) maxSizeMb = 5;
            long maxSizeBytes = maxSizeMb * 1024L * 1024L;

            if (f.length() > maxSizeBytes) {
                int len = (int) Math.min(f.length(), maxSizeBytes);
                byte[] buffer = new byte[len];
                FileInputStream fis = new FileInputStream(f);
                int off = 0;
                while (off < len) {
                    int r = fis.read(buffer, off, len - off);
                    if (r < 0) break;
                    off += r;
                }
                try { fis.close(); } catch (Throwable t) {}
                String content = new String(buffer, 0, off, "UTF-8");
                while (content.length() > 0 && content.charAt(content.length() - 1) == '\uFFFD') {
                    content = content.substring(0, content.length() - 1);
                }
                return content + "\n\n...(文件过大，已截断)";
            }
            String content = readWhole(f);
            return content == null ? "读取失败" : content;
        } catch (Throwable e) { return "读取失败: " + e.getMessage(); }
    }

    private String readWhole(File f) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    private String writeFile(File f, String content, boolean append) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, append);
            w.write(content == null ? "" : content);
            w.close();
            return "已" + (append ? "追加" : "写入") + "文件: " + f.getAbsolutePath()
                    + " (" + (content == null ? 0 : content.length()) + " 字符)";
        } catch (Throwable e) { return "写入失败: " + e.getMessage(); }
    }

    private String editFile(File f, JSONObject args) {
        if (!f.exists()) return "文件不存在: " + f.getAbsolutePath();
        if (f.isDirectory()) return "这是一个目录: " + f.getAbsolutePath();
        String oldS = optStr(args, "old_string");
        String newS = optStr(args, "new_string");
        boolean replaceAll = args.optBoolean("replace_all", false);
        if (oldS.length() == 0) return "old_string 不能为空";

        String content = readWhole(f);
        if (content == null) return "读取失败";

        // 统一换行符（CRLF 文件与 AI 端 \n 的差异会导致精确匹配失败）
        String normContent = content.replace("\r\n", "\n");
        String normOld = oldS.replace("\r\n", "\n");
        String normNew = newS.replace("\r\n", "\n");

        // 第一优先：精确匹配
        int first = normContent.indexOf(normOld);
        if (first >= 0) {
            if (!replaceAll) {
                int second = normContent.indexOf(normOld, first + normOld.length());
                if (second >= 0) return "old_string 出现多次，请提供更长唯一的片段，或把 replace_all 设为 true。";
            }
            try {
                String out;
                if (replaceAll) out = normContent.replace(normOld, normNew);
                else out = normContent.substring(0, first) + normNew + normContent.substring(first + normOld.length());
                FileWriter w = new FileWriter(f, false);
                w.write(out);
                w.close();
                int delta = newS.length() - oldS.length();
                return "已修改文件: " + f.getAbsolutePath()
                     + "（替换 " + (replaceAll ? "全部" : "1 处") + "，字符数变化 " + (delta >= 0 ? "+" : "") + delta + "）";
            } catch (Throwable e) { return "写入失败: " + e.getMessage(); }
        }

        // 第二优先：按行匹配、忽略每行行尾空白（AI 常在行尾多/少空格导致精确匹配失败）
        int win = blockMatch(normContent, normOld);
        if (win >= 0) {
            try {
                String[] cLines = normContent.split("\n", -1);
                String[] oLines = normOld.split("\n", -1);
                StringBuilder outB = new StringBuilder();
                for (int i = 0; i < win; i++) { outB.append(cLines[i]).append('\n'); }
                outB.append(normNew);
                if (!normNew.endsWith("\n")) outB.append('\n');
                for (int i = win + oLines.length; i < cLines.length; i++) {
                    outB.append(cLines[i]);
                    if (i < cLines.length - 1) outB.append('\n');
                }
                FileWriter w = new FileWriter(f, false);
                w.write(outB.toString());
                w.close();
                return "已修改文件: " + f.getAbsolutePath()
                     + "（1 处，按「忽略行尾空白」方式匹配成功。注意：下次请保证 old_string 与原文逐字符一致）";
            } catch (Throwable e) { return "写入失败: " + e.getMessage(); }
        }

        // 都失败：返回诊断信息，帮助 AI 自我修正
        return "未找到要替换的内容。old_string 必须与文件中原文逐字符一致。\n" + diagnose(normContent, normOld);
    }

    /** 整块滑动窗口匹配：按行 trimEnd 后逐行比较，命中返回起始行号，失败返回 -1 */
    private int blockMatch(String content, String oldS) {
        try {
            String[] cLines = content.split("\n", -1);
            String[] oLines = oldS.split("\n", -1);
            if (oLines.length == 0 || oLines.length > cLines.length) return -1;
            for (int start = 0; start + oLines.length <= cLines.length; start++) {
                boolean ok = true;
                for (int k = 0; k < oLines.length; k++) {
                    if (!trimEnd(cLines[start + k]).equals(trimEnd(oLines[k]))) { ok = false; break; }
                }
                if (ok) return start;
            }
        } catch (Throwable t) {}
        return -1;
    }

    private String trimEnd(String s) {
        int e = s.length();
        while (e > 0 && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\t')) e--;
        return s.substring(0, e);
    }

    /** 替换失败时定位最接近的片段位置，提示 AI 用 read_file_range 核对 */
    private String diagnose(String content, String oldS) {
        try {
            String[] cLines = content.split("\n", -1);
            String[] oLines = oldS.split("\n", -1);
            String probe = "";
            for (int i = 0; i < oLines.length; i++) {
                String t = oLines[i].trim();
                if (t.length() >= 6) { probe = t; break; }
            }
            if (probe.length() == 0 && oLines.length > 0) probe = oLines[0].trim();
            if (probe.length() > 0) {
                for (int i = 0; i < cLines.length; i++) {
                    if (cLines[i].trim().contains(probe)) {
                        return "诊断：文件中找不到与 old_string 完全一致的片段；第 " + (i + 1)
                             + " 行附近有相似内容。\n常见原因：① 缩进空格数不同；② 全角/半角标点差异；③ 内容来自旧版本文件。\n"
                             + "建议：先调用 read_file_range(path, " + Math.max(1, i - 5) + ", " + (i + oLines.length + 5)
                             + ") 获取带行号的真实内容，从返回结果中逐字符复制 old_string。";
                    }
                }
            }
        } catch (Throwable t) {}
        return "建议：先调用 read_file 或 read_file_range 重新读取该文件，从返回结果中逐字符复制 old_string（含缩进）。";
    }

    private String moveOrCopy(File src, File dst, boolean overwrite, boolean isMove) {
        try {
            if (!src.exists()) return "源不存在: " + src.getAbsolutePath();
            if (dst.exists() && !overwrite) return "目标已存在（如需覆盖请把 overwrite 设为 true）: " + dst.getAbsolutePath();
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            if (isMove) {
                if (src.renameTo(dst)) return "已移动: " + src.getAbsolutePath() + " → " + dst.getAbsolutePath();
                return "移动失败（可能跨分区），请改用 copy_file + delete_file";
            }
            if (src.isDirectory()) {
                int n = copyRecursive(src, dst);
                return "已复制目录: " + src.getAbsolutePath() + " → " + dst.getAbsolutePath() + "（" + n + " 个文件）";
            }
            copyFileRaw(src, dst);
            return "已复制文件: " + src.getAbsolutePath() + " → " + dst.getAbsolutePath();
        } catch (Throwable e) { return (isMove ? "移动" : "复制") + "失败: " + e.getMessage(); }
    }

    private void copyFileRaw(File src, File dst) throws IOException {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
        fis.close();
        fos.close();
    }

    private int copyRecursive(File src, File dst) {
        int n = 0;
        try {
            if (src.isDirectory()) {
                if (!dst.exists()) dst.mkdirs();
                File[] fs = src.listFiles();
                if (fs != null) for (int i = 0; i < fs.length; i++) {
                    n += copyRecursive(fs[i], new File(dst, fs[i].getName()));
                }
            } else {
                copyFileRaw(src, dst);
                n = 1;
            }
        } catch (Throwable t) {}
        return n;
    }

    private String deleteFile(File f, JSONObject args) {
        try {
            if (!f.exists()) return "文件不存在: " + f.getAbsolutePath();
            boolean recursive = args.optBoolean("recursive", false);
            if (f.isDirectory()) {
                if (!recursive) return "这是目录。若要连同内容一起删除，请把 recursive 设为 true。";
                int count = deleteRecursive(f);
                return "已删除目录: " + f.getAbsolutePath() + "（" + count + " 项）";
            }
            if (f.delete()) return "已删除文件: " + f.getAbsolutePath();
            return "删除失败（可能没有权限）: " + f.getAbsolutePath();
        } catch (Throwable e) { return "删除失败: " + e.getMessage(); }
    }

    private int deleteRecursive(File f) {
        int n = 0;
        try {
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                if (fs != null) for (int i = 0; i < fs.length; i++) n += deleteRecursive(fs[i]);
            }
            if (f.delete()) n++;
        } catch (Throwable t) {}
        return n;
    }

// ============================================================
// rename_file：只改名不移动（同目录内改名）
// ============================================================

private String renameFile(File src, String newName) {
    try {
        if (!src.exists()) return "文件不存在: " + src.getAbsolutePath();
        if (newName == null || newName.trim().length() == 0) return "new_name 不能为空";
        newName = newName.trim();
        // 禁止路径分隔符，防止用户通过 rename 逃逸出目录
        if (newName.indexOf('/') >= 0 || newName.indexOf('\\') >= 0) {
            return "new_name 只能填文件名，不能包含 / 或 \\。如果要移动到其他目录，请用 move_file。";
        }
        File parent = src.getParentFile();
        if (parent == null) return "无法获取父目录";
        File dst = new File(parent, newName);
        if (dst.exists() && !dst.getAbsolutePath().equals(src.getAbsolutePath())) {
            return "目标名已存在: " + newName;
        }
        if (src.getAbsolutePath().equals(dst.getAbsolutePath())) {
            return "新旧名字相同，无需重命名。";
        }
        if (src.renameTo(dst)) {
            return "已重命名: " + src.getName() + " → " + newName;
        }
        return "重命名失败（可能没有权限）: " + src.getAbsolutePath();
    } catch (Throwable e) {
        return "重命名失败: " + e.getMessage();
    }
}

    // ============================================================
    // 搜索
    // ============================================================

    private String searchFiles(File root, JSONObject args) {
        String kw = optStr(args, "keyword");
        boolean inContent = args.optBoolean("in_content", false);
        if (kw.length() == 0) return "keyword 不能为空";
        if (!root.exists()) return "路径不存在: " + root.getAbsolutePath();

        final String lowerKw = kw.toLowerCase();
        final List<String> results = new ArrayList<String>();
        final int[] scanned = new int[1];
        walk(root, lowerKw, inContent, results, scanned, 0);

        if (results.isEmpty()) {
            return "没有找到匹配「" + kw + "」的" + (inContent ? "内容" : "文件")
                 + "（扫描 " + scanned[0] + " 个文件）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词: ").append(kw)
          .append("（").append(inContent ? "按内容" : "按文件名").append("）\n")
          .append("范围: ").append(root.getAbsolutePath())
          .append("，扫描 ").append(scanned[0]).append(" 个文件，命中 ").append(results.size()).append(" 条\n\n");
        for (int i = 0; i < results.size(); i++) sb.append(results.get(i)).append("\n");
        if (results.size() >= MAX_SEARCH_RESULTS) sb.append("\n...(已达 ").append(MAX_SEARCH_RESULTS).append(" 条上限)\n");
        return sb.toString();
    }

    private void walk(File dir, String lowerKw, boolean inContent,
                      List<String> results, int[] scanned, int depth) {
        if (dir == null || depth > 12) return;
        if (results.size() >= MAX_SEARCH_RESULTS) return;
        if (scanned[0] >= MAX_SCAN_FILES) return;

        String name = dir.getName();
        if (name.startsWith(".") && depth > 0 && !".nomedia".equals(name)) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (int i = 0; i < files.length && results.size() < MAX_SEARCH_RESULTS && scanned[0] < MAX_SCAN_FILES; i++) {
            File f = files[i];
            try {
                if (f.isDirectory()) { walk(f, lowerKw, inContent, results, scanned, depth + 1); continue; }
                if (!f.isFile()) continue;
                scanned[0]++;

                if (!inContent) {
                    if (f.getName().toLowerCase().contains(lowerKw)) {
                        results.add("[文件] " + f.getAbsolutePath() + " (" + f.length() + " bytes)");
                    }
                    continue;
                }

                String fn = f.getName().toLowerCase();
                if (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".gif")
                        || fn.endsWith(".webp") || fn.endsWith(".mp3") || fn.endsWith(".mp4") || fn.endsWith(".apk")
                        || fn.endsWith(".zip") || fn.endsWith(".jar") || fn.endsWith(".so") || fn.endsWith(".ttf")
                        || fn.endsWith(".dex") || fn.endsWith(".class") || fn.endsWith(".db")) continue;
                if (f.length() > 2 * 1024 * 1024) continue;

                BufferedReader r = new BufferedReader(new FileReader(f));
                String line;
                int lineNo = 0;
                int hitsInFile = 0;
                while ((line = r.readLine()) != null) {
                    lineNo++;
                    if (line.toLowerCase().contains(lowerKw)) {
                        String show = line.trim();
                        if (show.length() > 160) show = show.substring(0, 160) + "…";
                        results.add(f.getAbsolutePath() + ":" + lineNo + ": " + show);
                        hitsInFile++;
                        if (hitsInFile >= 5 || results.size() >= MAX_SEARCH_RESULTS) break;
                    }
                }
                r.close();
            } catch (Throwable t) {}
        }
    }

    // ============================================================
    // 供界面使用
    // ============================================================

    public List<File> listDir(File dir) {
        List<File> out = new ArrayList<File>();
        try {
            File[] fs = dir.listFiles();
            if (fs == null) return out;
            List<File> dirs = new ArrayList<File>();
            List<File> files = new ArrayList<File>();
            for (int i = 0; i < fs.length; i++) {
                File f = fs[i];
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) dirs.add(f); else files.add(f);
            }
            Collections.sort(dirs);
            Collections.sort(files);
            out.addAll(dirs);
            out.addAll(files);
        } catch (Throwable t) {}
        return out;
    }

    public String readTextForUi(File f, int maxBytes) {
        try {
            long len = f.length();
            if (len > maxBytes) len = maxBytes;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) { int r = fis.read(buf, off, buf.length - off); if (r < 0) break; off += r; }
            fis.close();
            return new String(buf, 0, off, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    public File getWorkspace() { return workspace; }
}
