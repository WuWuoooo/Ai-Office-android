package com.ai.office;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * 本机 shell 工具（AI 的 run_shell_command），类似轻量 Termux 的顺序执行模式。
 *
 * 环境说明：
 *  - Android 原生 shell（toybox/mksh）没有 Python，因此 pip 不可用。
 *  - 已注入完整 PATH（/system/bin、/system/xbin、/vendor/bin 等），
 *    若设备上存在 python/node/perl 等解释器，可直接调用（AI 可先 which 探测）。
 *  - JS 生态请用 run_js 工具（系统内置 WebView 引擎，零依赖）。
 *
 * 安全约束：
 *  - 必须由用户在设置里开启 allow_shell_tool 才会注册给 AI
 *  - 以普通应用权限运行（无 root）；危险命令黑名单直接拒绝；超时强杀；输出截断
 *  - 每次调用在聊天工具面板中完整展示执行的命令，用户可见可审计
 */
public class ShellToolExecutor {

    private static final int MAX_OUTPUT = 100 * 1024;

    private static final String[] BLOCKLIST = {
            "rm -rf /", "rm -fr /", "rm -rf /*", "mkfs", "dd if=", "flash_image",
            "chmod 777 /", "chmod -r 777 /", "reboot", "shutdown", "poweroff",
            "settings put", "pm uninstall", "pm disable", ":(){", "fork bomb"
    };

    public static String execute(Context ctx, String argsJson) {
        Process proc = null;
        try {
            JSONObject args = new JSONObject(argsJson == null || argsJson.trim().length() == 0 ? "{}" : argsJson);
            String cmd = args.optString("command", "").trim();
            if (cmd.length() == 0) return "command 不能为空";

            String low = cmd.toLowerCase();
            for (int i = 0; i < BLOCKLIST.length; i++) {
                if (low.contains(BLOCKLIST[i])) {
                    return "已拒绝执行：命令命中危险黑名单（" + BLOCKLIST[i] + "）。"
                         + "如果你确实需要这类操作，请让用户手动执行。";
                }
            }

            int timeoutSec = args.optInt("timeout_sec", 15);
            if (timeoutSec <= 0) timeoutSec = 15;
            if (timeoutSec > 60) timeoutSec = 60;

            File cwd = new File("/sdcard");
            if (!cwd.exists()) cwd = new File("/");

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.directory(cwd);
            try {
                Map<String, String> env = pb.environment();
                env.put("PATH",
                        "/system/bin:/system/xbin:/vendor/bin:/odm/bin:/product/bin"
                      + ":/apex/com.android.runtime/bin:/apex/com.android.art/bin");
                env.put("HOME", cwd.getAbsolutePath());
                if (ctx != null) env.put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
                env.put("LANG", "en_US.UTF-8");
            } catch (Throwable t) {}
            proc = pb.start();

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

            String stdout = read(proc.getInputStream(), MAX_OUTPUT);
            String stderr = read(proc.getErrorStream(), 32 * 1024);
            int exit = proc.waitFor();
            killer.interrupt();

            StringBuilder sb = new StringBuilder();
            sb.append("exit code: ").append(exit).append("\n");
            sb.append("cwd: ").append(cwd.getAbsolutePath()).append("\n");
            if (stdout.length() > 0) sb.append("--- stdout ---\n").append(stdout).append("\n");
            if (stderr.length() > 0) sb.append("--- stderr ---\n").append(stderr).append("\n");
            if (stdout.length() == 0 && stderr.length() == 0) sb.append("（无输出）");

            // Python/pip 场景的环境提示：避免 AI 反复尝试 pip 而不明原因
            if (low.contains("python") || low.contains("pip")) {
                sb.append("\n--- 环境说明 ---\n")
                  .append("Android 原生 shell 一般没有 Python，pip 自然不可用。\n")
                  .append("先探测解释器：which python3 python pip node perl\n")
                  .append("若探测不到 python，pip 相关需求请改用：\n")
                  .append("① run_js 工具（内置 JS 引擎，可联网加载 JS 库并调用）；\n")
                  .append("② http 类型插件调用云端 API；\n")
                  .append("③ 引导用户安装 Termux，在其中 pkg install python 后通过插件使用。");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "命令执行失败: " + t.getMessage();
        } finally {
            if (proc != null) try { proc.destroy(); } catch (Throwable t) {}
        }
    }

    private static String read(InputStream is, int maxBytes) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            int total = 0;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
                total += n;
                if (total >= maxBytes) { sb.append("\n...(输出过长已截断)"); break; }
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
}
