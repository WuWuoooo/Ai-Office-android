package com.ai.office;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 内置浏览器：加载搜索卡片 / 网页阅读卡片点击的 URL。
 *
 * 顶部工具栏：返回键、前进键、刷新、外部浏览器打开、关闭
 * 标题栏：网页标题
 * 进度条：加载进度
 *
 * 零第三方依赖，纯 Android 原生 WebView。
 */
public class WebViewActivity extends BaseActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private WebView webView;
    private TextView tvTitle, btnBack, btnForward, btnReload, btnExternal, btnClose;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
            String title = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TITLE);
            if (url == null || url.trim().length() == 0) {
                Toast.makeText(this, "URL 为空", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            buildUi(title);
            loadUrl(url);
        } catch (Throwable t) {
            Toast.makeText(this, "内置浏览器启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildUi(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiUtils.color(this, R.color.md_surface));

        // ===== 顶部工具栏 =====
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(UiUtils.color(this, R.color.md_surface));
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 52)));
        topBar.setPadding(UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4), 0);

        btnBack = makeIconBtn("‹", "返回");
        btnBack.setTextSize(26);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoBack()) webView.goBack();
            }
        });
        topBar.addView(btnBack);

        btnForward = makeIconBtn("›", "前进");
        btnForward.setTextSize(26);
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoForward()) webView.goForward();
            }
        });
        topBar.addView(btnForward);

        // 标题（占据剩余宽度）
        tvTitle = new TextView(this);
        tvTitle.setText(title == null ? "浏览网页" : title);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(UiUtils.color(this, R.color.md_on_surface));
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        tlp.setMargins(UiUtils.dp(this, 6), 0, UiUtils.dp(this, 6), 0);
        topBar.addView(tvTitle, tlp);

        btnReload = makeIconBtn("↻", "刷新");
        btnReload.setTextSize(20);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null) webView.reload();
            }
        });
        topBar.addView(btnReload);

        btnExternal = makeIconBtn("⇱", "用外部浏览器");
        btnExternal.setTextSize(18);
        btnExternal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    String u = webView == null ? null : webView.getUrl();
                    if (u == null) return;
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                    startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(WebViewActivity.this, "无法打开外部浏览器", Toast.LENGTH_SHORT).show();
                }
            }
        });
        topBar.addView(btnExternal);

        btnClose = makeIconBtn("×", "关闭");
        btnClose.setTextSize(22);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnClose);

        root.addView(topBar);

        // 分隔线
        View divider = new View(this);
        divider.setBackgroundColor(UiUtils.color(this, R.color.md_divider));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 1)));

        // ===== 进度条 =====
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 3));
        root.addView(progress, plp);

        // ===== WebView =====
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setSupportZoom(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        try { ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); } catch (Throwable t) {}

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    view.loadUrl(url);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progress != null) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(5);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progress != null) {
                    progress.setVisibility(View.GONE);
                }
                updateNavButtons();
                try {
                    String t = view.getTitle();
                    if (t != null && t.length() > 0 && tvTitle != null) {
                        tvTitle.setText(t);
                    }
                } catch (Throwable tt) {}
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (progress != null) progress.setVisibility(View.GONE);
                Toast.makeText(WebViewActivity.this,
                        "加载失败: " + description + "（" + errorCode + "）", Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (progress != null) {
                    progress.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progress.setVisibility(View.GONE);
                    } else if (progress.getVisibility() != View.VISIBLE) {
                        progress.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(webView, wlp);

        setContentView(root);
    }

    /** 顶部图标按钮的统一样式 */
    private TextView makeIconBtn(String text, String desc) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setTextColor(UiUtils.color(this, R.color.md_on_surface));
        tv.setGravity(Gravity.CENTER);
        tv.setContentDescription(desc);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                UiUtils.dp(this, 40), UiUtils.dp(this, 40)));
        tv.setBackgroundResource(R.drawable.circle_btn_bg);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private void updateNavButtons() {
        try {
            boolean canBack = webView != null && webView.canGoBack();
            boolean canFwd = webView != null && webView.canGoForward();
            if (btnBack != null) {
                btnBack.setAlpha(canBack ? 1.0f : 0.35f);
            }
            if (btnForward != null) {
                btnForward.setAlpha(canFwd ? 1.0f : 0.35f);
            }
        } catch (Throwable t) {}
    }

    private void loadUrl(String url) {
        try {
            if (webView != null) webView.loadUrl(url);
        } catch (Throwable t) {
            Toast.makeText(this, "加载失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return;
            }
        } catch (Throwable t) {}
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.setWebViewClient(null);
                webView.setWebChromeClient(null);
                webView.removeAllViews();
                webView.destroy();
                webView = null;
            }
        } catch (Throwable t) {}
        super.onDestroy();
    }
}