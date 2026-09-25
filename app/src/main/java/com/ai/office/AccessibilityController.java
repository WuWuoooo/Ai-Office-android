package com.ai.office;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍执行器：把 AI 的 accessibility_control 工具调用转成实际手机操作。
 *
 * 动作集：screen / tap / click_text / input / back / home / scroll /
 *        launch_app（启动应用）/ sleep（等待界面加载）/ recents / notifications
 *
 * 安全边界：
 *  - 只有用户在系统设置里开启本 App 的无障碍服务后 sService 才非空
 *  - 只有用户在 App 设置里开启 allow_accessibility_tool 后工具才会注册给 AI
 *  - 每次操作都会显示在聊天工具面板里，用户可见可审计
 */
public class AccessibilityController {

    private static volatile AccessibilityService sService = null;
    private static final int MAX_NODES = 250;

    public static void setService(AccessibilityService s) { sService = s; }

    public static boolean isReady() { return sService != null; }

    public static String execute(Context ctx, String argsJson) {
        try {
            JSONObject args = new JSONObject(argsJson == null || argsJson.trim().length() == 0 ? "{}" : argsJson);
            String action = args.optString("action", "").trim();

            // launch_app 不依赖无障碍服务（用 PackageManager 启动），单独处理
            if ("launch_app".equals(action)) {
                return launchApp(ctx, args.optString("package", ""), args.optString("app_name", ""));
            }
            // 停止屏幕共享：也不依赖无障碍服务；视觉任务完成后 AI 应主动调用
            if ("stop_projection".equals(action)) {
                ProjectionController.stop();
                return "已停止屏幕共享，状态栏的「屏幕共享中」通知会消失。";
            }

            AccessibilityService svc = sService;
            if (svc == null) {
                return "无障碍服务未连接。请引导用户：系统设置 → 无障碍 → AI Office「手机操控」→ 开启。"
                     + "（App 内的「允许 AI 操控手机」开关已开，仅差系统服务授权。）";
            }

            if ("screen".equals(action)) return readScreen(svc);
            if ("tap".equals(action)) return tap(svc, args.optInt("x", -1), args.optInt("y", -1), args.optBoolean("from_vision", false));
            if ("long_press".equals(action)) return longPress(svc, args.optInt("x", -1), args.optInt("y", -1), args.optBoolean("from_vision", false));
            if ("swipe".equals(action)) return swipe(svc, args.optInt("x1", -1), args.optInt("y1", -1), args.optInt("x2", -1), args.optInt("y2", -1), args.optInt("duration_ms", 400), args.optBoolean("from_vision", false));
            if ("screenshot".equals(action)) return screenshot(ctx);
            if ("windows".equals(action)) return listWindows(svc);
            if ("click_text".equals(action)) return clickText(svc, args.optString("text", ""));
            if ("input".equals(action)) return inputText(ctx, svc, args.optString("text", ""));
            if ("back".equals(action)) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                return "已执行返回键";
            }
            if ("home".equals(action)) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                return "已回到桌面";
            }
            if ("recents".equals(action)) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                return "已打开最近任务";
            }
            if ("notifications".equals(action)) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                return "已拉出通知栏";
            }
            if ("scroll".equals(action)) return scroll(svc, args.optString("direction", "down"));
            if ("sleep".equals(action)) return sleepFor(args.optInt("ms", 1000));
            return "未知 action: " + action
                 + "（可用: screen/screenshot/tap/long_press/click_text/input/back/home/scroll/launch_app/sleep/recents/notifications/windows/stop_projection）";
        } catch (Throwable t) {
            return "无障碍操作失败: " + t.getMessage();
        }
    }

    // ---------- 启动应用（不需要无障碍服务） ----------

    private static String launchApp(Context ctx, String pkg, String appName) {
        try {
            if (ctx == null) return "无法获取应用上下文";
            PackageManager pm = ctx.getPackageManager();
            String target = pkg == null ? "" : pkg.trim();

            if (target.length() == 0) {
                String kw = appName == null ? "" : appName.trim().toLowerCase();
                if (kw.length() == 0) return "需要提供 package（包名，如 com.tencent.mm）或 app_name（应用名关键词）";
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                List<String> hits = new ArrayList<String>();
                target = "";
                for (int i = 0; i < apps.size(); i++) {
                    ApplicationInfo ai = apps.get(i);
                    String label;
                    try { label = String.valueOf(pm.getApplicationLabel(ai)); } catch (Throwable t) { label = ""; }
                    if (label.toLowerCase().contains(kw) || ai.packageName.toLowerCase().contains(kw)) {
                        if (target.length() == 0) target = ai.packageName;
                        if (hits.size() < 6) hits.add(label + " = " + ai.packageName);
                    }
                }
                if (target.length() == 0) return "没有找到名称或包名包含「" + appName + "」的应用";
                if (hits.size() > 1) return "匹配到多个应用，请用 package 指定后重试: " + hits;
            }

            Intent it = pm.getLaunchIntentForPackage(target);
            if (it == null) return "应用 " + target + " 没有可启动的入口（可能未安装或被停用）";
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
            try { Thread.sleep(1200L); } catch (Throwable t) {}
            return "已启动 " + target + "。建议先 sleep 500~1000ms 等界面加载，再 action=screen 查看当前界面。";
        } catch (Throwable t) {
            return "启动失败: " + t.getMessage();
        }
    }

    // ---------- 等待 ----------

    private static String sleepFor(int ms) {
        if (ms <= 0) ms = 500;
        if (ms > 5000) ms = 5000;
        try { Thread.sleep(ms); } catch (Throwable t) {}
        return "已等待 " + ms + " ms";
    }

    // ---------- 读取屏幕 ----------

    private static String readScreen(AccessibilityService svc) {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return "无法读取当前屏幕（目标应用可能禁止无障碍读取，或不在任何界面上）";
        StringBuilder sb = new StringBuilder();
        CharSequence pk = root.getPackageName();
        sb.append("前台应用包名: ").append(pk == null ? "(未知)" : pk.toString()).append("\n");
        sb.append("当前屏幕控件（文本 | 中心坐标 | 可点击）:\n\n");
        int[] counter = new int[1];
        dfsScreen(root, sb, counter);
        if (counter[0] == 0) {
            sb.append("（未读到任何控件！）\n")
              .append("可能原因：① 该应用限制了无障碍读取（微信等大型 App 常见）；② 界面尚在加载。\n")
              .append("替代方案：调用 action=screenshot 截屏，图片会自动附到对话里，看图返回坐标即可完成同样的操作。");
        }
        return sb.toString();
    }

    private static void dfsScreen(AccessibilityNodeInfo node, StringBuilder sb, int[] counter) {
        if (node == null || counter[0] >= MAX_NODES) return;
        counter[0]++;
        try {
            CharSequence txt = node.getText();
            CharSequence desc = node.getContentDescription();
            String t = txt == null ? "" : txt.toString().trim();
            String d = desc == null ? "" : desc.toString().trim();
            if (t.length() > 0 || d.length() > 0 || node.isClickable()) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                String label = t.length() > 0 ? t : d;
                if (label.length() > 60) label = label.substring(0, 60) + "…";
                if (label.length() == 0) label = "(未命名控件)";
                sb.append("「").append(label).append("」")
                  .append(" (").append(r.centerX()).append(",").append(r.centerY()).append(")")
                  .append(node.isClickable() ? " [可点击]" : "").append("\n");
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                dfsScreen(node.getChild(i), sb, counter);
            }
        } catch (Throwable t) {}
    }

    // ---------- 点击 ----------

    // ---------- 手势（点击 / 长按 / 滑动） ----------

    /** 视觉坐标换算：截图坐标 → 实际屏幕坐标。
     *  ★ 分母必须是「发给 AI 的图片尺寸」（screenshot 里可能已缩放到 720 宽），
     *    不是投影原始分辨率，否则比例恒为 1.0 等于没换算，点击会系统性偏移。 */
    private static int[] mapCoords(int x, int y, boolean fromVision) {
        try {
            if (fromVision && ProjectionController.visionImageWidth() > 0 && ProjectionController.visionImageHeight() > 0) {
                float rx = (float) ProjectionController.screenWidth() / (float) ProjectionController.visionImageWidth();
                float ry = (float) ProjectionController.screenHeight() / (float) ProjectionController.visionImageHeight();
                x = Math.round(x * rx);
                y = Math.round(y * ry);
            }
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (ProjectionController.screenWidth() > 0 && x > ProjectionController.screenWidth() - 1) x = ProjectionController.screenWidth() - 1;
            if (ProjectionController.screenHeight() > 0 && y > ProjectionController.screenHeight() - 1) y = ProjectionController.screenHeight() - 1;
        } catch (Throwable t) {}
        return new int[]{x, y};
    }

    /** from_vision=true 时校验有可用的截图尺寸记录；没有则返回错误文案（App 重启/停止投屏后旧坐标已失效） */
    private static String visionGuard(boolean fromVision) {
        if (fromVision && ProjectionController.visionImageWidth() <= 0) {
            return "视觉坐标换算失败：当前没有已记录的截图尺寸（App 重启或投屏停止后，旧截图的坐标已失效）。"
                 + "请先调用 action=screenshot 获取新截图，再按新图上的像素坐标返回操作（from_vision=true）。";
        }
        return null;
    }

    private static String tap(final AccessibilityService svc, int x, int y, boolean fromVision) {
        String guard = visionGuard(fromVision);
        if (guard != null) return guard;
        int ox = x, oy = y;
        int[] p = mapCoords(x, y, fromVision);
        x = p[0]; y = p[1];
        if (x < 0 || y < 0) return "需要提供 x、y 坐标（可先用 action=screen 或 action=screenshot 获取）";
        if (Build.VERSION.SDK_INT < 24) return "当前系统版本不支持模拟点击（需要 Android 7.0+）";
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, 60L));
            svc.dispatchGesture(b.build(), null, null);
            return "已在屏幕 (" + x + ", " + y + ") 模拟点击"
                 + (fromVision ? "（由截图坐标 (" + ox + ", " + oy + ") 按比例换算）" : "")
                 + "。若界面未变化，建议 sleep 800ms 后再 action=screen 或 action=screenshot 确认。";
        } catch (Throwable t) {
            return "点击失败: " + t.getMessage();
        }
    }

    private static String longPress(final AccessibilityService svc, int x, int y, boolean fromVision) {
        String guard = visionGuard(fromVision);
        if (guard != null) return guard;
        int ox = x, oy = y;
        int[] p = mapCoords(x, y, fromVision);
        x = p[0]; y = p[1];
        if (x < 0 || y < 0) return "需要提供 x、y 坐标";
        if (Build.VERSION.SDK_INT < 24) return "当前系统版本不支持模拟长按（需要 Android 7.0+）";
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, 800L));
            svc.dispatchGesture(b.build(), null, null);
            return "已在屏幕 (" + x + ", " + y + ") 模拟长按 800ms"
                 + (fromVision ? "（由截图坐标 (" + ox + ", " + oy + ") 按比例换算）" : "")
                 + "。建议 sleep 1000ms 后确认结果。";
        } catch (Throwable t) {
            return "长按失败: " + t.getMessage();
        }
    }

    private static String swipe(final AccessibilityService svc, int x1, int y1, int x2, int y2,
                                int durationMs, boolean fromVision) {
        String guard = visionGuard(fromVision);
        if (guard != null) return guard;
        int ox1 = x1, oy1 = y1, ox2 = x2, oy2 = y2;
        int[] a = mapCoords(x1, y1, fromVision);
        int[] c = mapCoords(x2, y2, fromVision);
        x1 = a[0]; y1 = a[1]; x2 = c[0]; y2 = c[1];
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return "需要提供 x1,y1,x2,y2（起点和终点坐标）";
        if (Build.VERSION.SDK_INT < 24) return "当前系统版本不支持模拟滑动（需要 Android 7.0+）";
        if (durationMs < 100) durationMs = 100;
        if (durationMs > 3000) durationMs = 3000;
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
            svc.dispatchGesture(b.build(), null, null);
            return "已从屏幕 (" + x1 + ", " + y1 + ") 滑动到 (" + x2 + ", " + y2 + ")，用时 " + durationMs + "ms"
                 + (fromVision ? "（由截图坐标 (" + ox1 + "," + oy1 + ")→(" + ox2 + "," + oy2 + ") 换算）" : "")
                 + "。建议 sleep 1000ms 后确认结果。";
        } catch (Throwable t) {
            return "滑动失败: " + t.getMessage();
        }
    }

    // ---------- 截屏（视觉操控） ----------

    private static String screenshot(Context ctx) {
        try {
            if (!ProjectionController.isReady()) {
                // 未授权：自动帮用户拉起系统授权弹窗
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).requestScreenProjection();
                    return "尚未授权截屏。系统已弹出「屏幕录制授权」弹窗，请点击「立即开始」，然后重新调用 action=screenshot。";
                }
                return "需要先授权截屏（MediaProjection）";
            }
            Bitmap bmp = ProjectionController.capture();
            if (bmp == null) return "截屏失败（可能刚授权还没出首帧，或会话已失效）。请稍等 1 秒后重试；若多次失败请重新授权。";
            bmp = drawGridOverlay(bmp);   // 叠加 10% 网格与像素刻度，帮助视觉模型按刻度精确定位
            int maxW = 720;
            if (bmp.getWidth() > maxW) {
                float r = (float) maxW / (float) bmp.getWidth();
                Bitmap sc = Bitmap.createScaledBitmap(bmp, maxW, Math.max(1, Math.round(bmp.getHeight() * r)), true);
                if (sc != bmp) bmp.recycle();
                bmp = sc;
            }
            // ★ 记录「发给 AI 的图片」实际尺寸：from_vision 坐标换算以此为基准
            ProjectionController.setVisionImageSize(bmp.getWidth(), bmp.getHeight());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, bos);
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return "@@SHOT@@" + b64;
        } catch (Throwable t) {
            return "截屏失败: " + t.getMessage();
        }
    }

    /** 在截图上叠加 10% 网格线（半透明细线）与边缘像素刻度，帮视觉模型把"看到的元素"落到精确像素坐标。
     *  零依赖实现（Canvas/Paint）；任何一步失败都返回原图，不影响截屏主流程。 */
    private static Bitmap drawGridOverlay(Bitmap src) {
        Bitmap bmp;
        try { bmp = src.copy(Bitmap.Config.ARGB_8888, true); } catch (Throwable t) { return src; }
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            Canvas c = new Canvas(bmp);
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setColor(0x5AFF4444);          // 半透明红细线，尽量不遮挡内容
            lp.setStrokeWidth(1.5f);
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(0xFFD32F2F);
            tp.setTextSize(Math.max(16f, w / 34f));
            tp.setShadowLayer(3f, 1f, 1f, 0xFFFFFFFF);   // 白描边，浅色背景也可读
            int n = 10;
            for (int i = 1; i < n; i++) {
                int gx = w * i / n;
                int gy = h * i / n;
                c.drawLine(gx, 0, gx, h, lp);
                c.drawLine(0, gy, w, gy, lp);
                c.drawText(String.valueOf(gx), gx + 4, tp.getTextSize() + 2, tp);   // 顶部标 x 刻度
                c.drawText(String.valueOf(gy), 4, gy - 5, tp);                      // 左侧标 y 刻度
            }
            c.drawText("0,0", 4, tp.getTextSize() + 2, tp);
        } catch (Throwable t) {}
        return bmp;
    }

    // ---------- 窗口诊断 ----------

    private static String listWindows(AccessibilityService svc) {
        try {
            List<AccessibilityWindowInfo> ws = svc.getWindows();
            if (ws == null || ws.isEmpty()) return "没有可读窗口（服务可能未获取窗口检索权限）";
            StringBuilder sb = new StringBuilder("当前窗口列表（type/layer/active/focused/根节点有无内容）:\n");
            for (int i = 0; i < ws.size(); i++) {
                AccessibilityWindowInfo w = ws.get(i);
                AccessibilityNodeInfo r = w.getRoot();
                int kids = r == null ? -1 : r.getChildCount();
                sb.append("#").append(i)
                  .append(" type=").append(w.getType())
                  .append(" layer=").append(w.getLayer())
                  .append(" active=").append(w.isActive())
                  .append(" focused=").append(w.isFocused())
                  .append(" 根节点子项=").append(kids).append("\n");
            }
            sb.append("\n提示：若 active 窗口子项为 -1 或 0，该应用可能限制无障碍读取，请改用 action=screenshot。");
            return sb.toString();
        } catch (Throwable t) {
            return "读取窗口失败: " + t.getMessage();
        }
    }

    private static String clickText(final AccessibilityService svc, String text) {
        if (text == null || text.trim().length() == 0) return "需要提供 text（要点击的控件文字）";
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return "无法获取当前屏幕节点（目标应用可能限制了无障碍）";
        int[] counter = new int[1];
        AccessibilityNodeInfo hit = dfsFindText(root, text.trim(), counter);
        if (hit == null) {
            return "屏幕上没有找到包含「" + text + "」的控件。可先用 action=screen 查看当前屏幕内容。";
        }
        AccessibilityNodeInfo clickable = hit;
        int depth = 0;
        while (clickable != null && !clickable.isClickable() && depth < 6) {
            clickable = clickable.getParent();
            depth++;
        }
        if (clickable != null && clickable.isClickable()) {
            boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return ok ? "已点击「" + text + "」。若界面未变化，sleep 800ms 后 action=screen 确认。"
                      : "点击执行失败";
        }
        Rect r = new Rect();
        hit.getBoundsInScreen(r);
        if (Build.VERSION.SDK_INT >= 24) {
            return tap(svc, r.centerX(), r.centerY(), false) + "（控件本身不可点击，已尝试坐标点击）";
        }
        return "该控件不可点击";
    }

    private static AccessibilityNodeInfo dfsFindText(AccessibilityNodeInfo node, String text, int[] counter) {
        if (node == null || counter[0] >= MAX_NODES) return null;
        counter[0]++;
        try {
            CharSequence txt = node.getText();
            CharSequence desc = node.getContentDescription();
            if ((txt != null && txt.toString().contains(text))
                    || (desc != null && desc.toString().contains(text))) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo r = dfsFindText(node.getChild(i), text, counter);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    // ---------- 输入 ----------

    private static String inputText(Context ctx, AccessibilityService svc, String text) {
        if (text == null || text.trim().length() == 0) return "需要提供 text（要输入的文本）";
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        AccessibilityNodeInfo target = null;
        if (root != null) {
            try { target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable t) {}
            if (target == null || !target.isEditable()) {
                int[] counter = new int[1];
                target = dfsFindEditable(root, counter);
            }
        }
        if (target == null) {
            return "当前屏幕读不到可输入的节点（目标 App 可能限制无障碍读取，或输入框尚未获得焦点）。"
                 + "标准流程：① tap 点击输入框使其聚焦（视觉坐标传 from_vision=true）；"
                 + "② sleep 800ms 等输入法弹出；③ 重新调用 action=input。";
        }
        try { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS); } catch (Throwable t) {}
        // 第 1 层：ACTION_SET_TEXT（多数标准输入框有效）
        try {
            Bundle bd = new Bundle();
            bd.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bd)) {
                return "已向输入框写入文本。需要发送时继续 click_text「发送」或 tap 发送按钮。";
            }
        } catch (Throwable t) {}
        // 第 2 层：写入剪贴板 + ACTION_PASTE（SET_TEXT 无效时常见有效的兜底）
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("aio_input", text));
            if (target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return "已通过剪贴板粘贴写入文本（SET_TEXT 无效，走 PASTE 兜底成功）。注意：用户剪贴板已被覆盖。";
            }
        } catch (Throwable t) {}
        // 第 3 层：至少把文本放进剪贴板，用户长按输入框即可粘贴
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("aio_input", text));
        } catch (Throwable t) {}
        return "无法直接写入（SET_TEXT 与剪贴板粘贴均失败）。文本已复制到剪贴板，"
             + "可引导用户在输入框长按 → 粘贴，或建议用户手动输入。";
    }

    private static AccessibilityNodeInfo dfsFindEditable(AccessibilityNodeInfo node, int[] counter) {
        if (node == null || counter[0] >= MAX_NODES) return null;
        counter[0]++;
        try {
            if (node.isEditable() && node.isFocusable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo r = dfsFindEditable(node.getChild(i), counter);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    // ---------- 滚动 ----------

    private static String scroll(final AccessibilityService svc, String direction) {
        if (Build.VERSION.SDK_INT < 24) return "当前系统版本不支持模拟滑动（需要 Android 7.0+）";
        boolean up = "up".equalsIgnoreCase(direction);
        Rect r = new Rect();
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root != null) root.getBoundsInScreen(r);
        if (r.width() <= 0 || r.height() <= 0) r.set(0, 0, 1080, 1920);
        int cx = r.centerX();
        int y1 = up ? (int) (r.top + r.height() * 0.7f) : (int) (r.top + r.height() * 0.3f);
        int y2 = up ? (int) (r.top + r.height() * 0.3f) : (int) (r.top + r.height() * 0.7f);
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(cx, y1);
            path.lineTo(cx, y2);
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, 300L));
            svc.dispatchGesture(b.build(), null, null);
            return up ? "已向上滑动" : "已向下滑动";
        } catch (Throwable t) {
            return "滑动失败: " + t.getMessage();
        }
    }
}
