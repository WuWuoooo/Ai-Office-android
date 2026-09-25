package com.ai.office;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * run_js：用系统内置 WebView（V8 引擎）执行 JavaScript，零依赖。
 *
 * - 基址为 file:/// 且放开了跨域，JS 里可以用同步 XMLHttpRequest 拉取任意远程 JS 库再 eval
 * - 代码中调用 __send(结果) 返回结果；对象会自动 JSON 序列化
 * - 同步执行完 2.5 秒仍无 __send 会自动收尾；整体受 timeout_sec 硬超时保护
 *
 * 安全约束：随 shell 开关一起注册（设置里开启「允许 AI 执行命令」后可用）。
 */
public class JsToolExecutor {

    public static String execute(final Context ctx, String argsJson) {
        try {
            final JSONObject args = new JSONObject(argsJson == null || argsJson.trim().length() == 0 ? "{}" : argsJson);
            final String code = args.optString("code", "").trim();
            if (code.length() == 0) return "code 不能为空";
            int timeoutSec = args.optInt("timeout_sec", 15);
            if (timeoutSec <= 0) timeoutSec = 15;
            if (timeoutSec > 60) timeoutSec = 60;
            final int fTimeout = timeoutSec;

            if (ctx == null) return "无法获取应用上下文";
            final Context appCtx = ctx.getApplicationContext();

            final AtomicReference<String> out = new AtomicReference<String>(null);
            final CountDownLatch latch = new CountDownLatch(1);
            final Handler main = new Handler(Looper.getMainLooper());
            final WebView[] holder = new WebView[1];

            main.post(new Runnable() {
                @Override public void run() {
                    try {
                        WebView wv = new WebView(appCtx);
                        holder[0] = wv;
                        WebSettings st = wv.getSettings();
                        st.setJavaScriptEnabled(true);
                        st.setDomStorageEnabled(true);
                        st.setAllowFileAccess(true);
                        // file:/// 基址下放开跨域，让 XHR 能拉取 CDN 上的 JS 库
                        try { st.setAllowUniversalAccessFromFileURLs(true); } catch (Throwable t) {}
                        st.setAllowContentAccess(false);
                        wv.setWebViewClient(new WebViewClient());
                        wv.addJavascriptInterface(new Bridge(out, latch), "__aio");

                        // 防止用户代码里的 </script> 截断 HTML
                        String user = code.replace("</script", "<\\/script");
                        String html = "<html><head><script>"
                                + "window.onerror=function(m,u,l){__aio.result('JS错误: '+m+' (行 '+l+')');};"
                                + "function __send(v){__aio.result(typeof v==='string'?v:JSON.stringify(v));}"
                                + "</script></head><body><script>"
                                + user
                                + "\n;(function(){setTimeout(function(){__aio.result('(代码已同步执行完毕，但未调用 __send 返回结果)');},2500);})();"
                                + "</script></body></html>";
                        wv.loadDataWithBaseURL("file:///", html, "text/html", "utf-8", null);
                    } catch (Throwable t) {
                        try { out.compareAndSet(null, "JS 引擎初始化失败: " + t.getMessage()); } catch (Throwable tt) {}
                        try { latch.countDown(); } catch (Throwable tt) {}
                    }
                }
            });

            boolean finished = latch.await(fTimeout, TimeUnit.SECONDS);

            // 无论结果如何，销毁 WebView 释放资源
            main.post(new Runnable() {
                @Override public void run() {
                    try { if (holder[0] != null) holder[0].destroy(); } catch (Throwable t) {}
                }
            });

            if (!finished && out.get() == null) {
                return "JS 执行超时（" + fTimeout + " 秒）。"
                     + "如果代码是异步的（网络回调/setTimeout），请在完成时调用 __send(结果)，并增大 timeout_sec。";
            }
            String r = out.get();
            if (r == null) r = "(无返回值)";
            return "JS 执行结果:\n" + r;
        } catch (Throwable t) {
            return "run_js 执行失败: " + t.getMessage();
        }
    }

    /** JS 与宿主的桥：JS 里通过 __aio.result(...) 或 __send(...) 上报结果 */
    private static class Bridge {
        private final AtomicReference<String> out;
        private final CountDownLatch latch;

        Bridge(AtomicReference<String> out, CountDownLatch latch) {
            this.out = out;
            this.latch = latch;
        }

        @JavascriptInterface
        public void result(String v) {
            try {
                String val = (v == null || v.length() == 0) ? "(null)" : v;
                if (val.length() > 200000) val = val.substring(0, 200000) + "\n...(结果过长已截断)";
                if (out.compareAndSet(null, val)) latch.countDown();
            } catch (Throwable t) {}
        }
    }
}
