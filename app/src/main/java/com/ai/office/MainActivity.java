package com.ai.office;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {

    private static final String PREFS = "ai_office_config";
    private static final int REQ_IMG_PICK = 2001;
    private static final int REQ_IMG_CAMERA = 2002;
    private static final int REQ_IMPORT_MD = 3101;
    private static final int REQ_IMPORT_JSON = 3102;
    private static final int REQ_PROJECTION = 4001;
    private static final int REQ_VOICE_CALL = 5001;
    private static final int STREAM_FLUSH_STEP = 800;
    private static final int MAX_RENDER_MESSAGES = 60;
    private static final long DRAFT_INTERVAL = 1500L;
    private static final long CLICK_GUARD = 350L;
    private static final int AT_FILE_MAX = 20000;

    private static final String DEFAULT_SYSTEM_PROMPT =
            "现在你拥有skill，可以读取与修改文件\n" +
            "你拥有以下skill（通过工具调用实现）：查看目录、读取文件、创建文件、修改文件、精确替换文件内容、搜索文件、联网搜索。\n" +
            "1. 仅在有需要时才调用工具。\n" +
            "2. 需要读取多个文件时，请一次性同时调用多个 read_file。\n" +
            "3. 修改已有文件中的一小部分时，优先使用 edit_file（精确替换），不要用 write_file 全文重写。\n" +
            "4. 需要实时信息时用 web_search；需要读具体页面时用 fetch_url。\n" +
            "5. 不知道文件在哪时，用 search_files 搜索，不要盲目逐个目录列。\n" +
            "6. 获取足够信息后，立即停止调用工具，直接输出自然语言总结。";

    private ScrollView svMessages;
    private LinearLayout llMessages;
    private LinearLayout layoutWelcome;
    private LinearLayout layoutDrawer;
    private LinearLayout llHistoryList;
    private LinearLayout llImagePreview;
    private LinearLayout llMainRoot;
    private ImageView ivPreview;
    private TextView btnSend, tvTitle, tvModelName, tvTokenStats;
    private EditText etInput, etSessionSearch;

    private LinearLayout llQuoteBar;
    private TextView tvQuoteText;
    private int quoteIndex = -1;
    private String quoteText = "";

    private AiClient aiClient;
    private FileToolExecutor fileExecutor;
    private WebToolExecutor webExecutor;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private JSONArray tools;
    private JSONArray messages = new JSONArray();
    private JSONArray sessionsJson = new JSONArray();
    private String currentSessionId = "";
    private String sessionFilter = "";
    private int currentPromptTokens = 0;
    private int currentCompletionTokens = 0;
    private int currentCacheHitTokens = 0;
    private int renderLimit = MAX_RENDER_MESSAGES;

    private LinearLayout currentAiContainer;
    private TextView currentReasoningHeader, currentReasoningContent, currentContentTv;
    private StringBuilder roundReasoning = new StringBuilder();
    private StringBuilder roundContent = new StringBuilder();
    private long reasoningStartTime = 0L;
    private boolean isReasoningActive = false;
    private boolean toolPhaseActive = false;
    private boolean isGenerating = false;
    private boolean shouldStop = false;
    private boolean alive = true;
    private boolean inForeground = false;
    private boolean compressing = false;
    private View stoppedRow = null;
    private List<String> pendingImageBase64List = new ArrayList<String>();
    private List<byte[]> pendingImageBytesList = new ArrayList<byte[]>();
    private final List<File> pendingRefFiles = new ArrayList<File>();

    private JSONArray pendingToolIds = new JSONArray();
    private boolean discardToolResults = false;
    private int toolBatchId = 0;
    private int lastRenderedLen = 0;
    private long lastDraftTime = 0L;
    private long lastClickTime = 0L;

    private static class ToolOutcome {
        String id; String name; String args; String result;
    }

    private String t(String key, String def) { return LanguageManager.t(this, key, def); }

    private void toast(String key, String def) {
        try { Toast.makeText(this, t(key, def), Toast.LENGTH_SHORT).show(); } catch (Throwable th) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            registerReceiver(bgRefreshReceiver, new IntentFilter("com.ai.office.REFRESH_BG"));
            try { getWindow().setWindowAnimations(0); } catch (Throwable t) {}
            alive = true;
            applySystemBars();

            setContentView(R.layout.activity_main);
            applyTopbarIcons();
            applyThemeChrome();
            svMessages = (ScrollView) findViewById(R.id.svMessages);
            llMessages = (LinearLayout) findViewById(R.id.llMessages);
            layoutWelcome = (LinearLayout) findViewById(R.id.layoutWelcome);
            layoutDrawer = (LinearLayout) findViewById(R.id.layoutDrawer);
            llHistoryList = (LinearLayout) findViewById(R.id.llHistoryList);
            llImagePreview = (LinearLayout) findViewById(R.id.llImagePreview);
            ivPreview = (ImageView) findViewById(R.id.ivPreview);
            btnSend = (TextView) findViewById(R.id.btnSend);
            tvTitle = (TextView) findViewById(R.id.tvTitle);
            tvModelName = (TextView) findViewById(R.id.tvModelName);
            tvTokenStats = (TextView) findViewById(R.id.tvTokenStats);
            etInput = (EditText) findViewById(R.id.etInput);
            etSessionSearch = (EditText) findViewById(R.id.etSessionSearch);
            llQuoteBar = (LinearLayout) findViewById(R.id.llQuoteBar);
            tvQuoteText = (TextView) findViewById(R.id.tvQuoteText);
            try { llMainRoot = (LinearLayout) findViewById(R.id.llMainRoot); } catch (Throwable t) {}
            TextView btnClearQuote = (TextView) findViewById(R.id.btnClearQuote);
            if (btnClearQuote != null) {
                btnClearQuote.setText(t("main_quote_cancel", "取消引用"));
                btnClearQuote.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { clearQuote(); }
                });
            }

            applyUiTexts();

            fileExecutor = new FileToolExecutor(this);
            webExecutor = new WebToolExecutor(this);
            refreshTools();

            requestStoragePermission();
            requestNotificationPermission();
            loadConfig();
            updateModelLabel();

            loadSessionIndex();

            boolean draftRestored = restoreDraftIfAny();
            if (!draftRestored) {
                String startupMode = UiUtils.getStr(this, "startup_mode", "last");
                if ("new".equals(startupMode)) {
                    initNewSession();
                } else {
                    int latestIdx = findLatestSessionIndex();
                    if (latestIdx >= 0) {
                        JSONObject last = sessionsJson.optJSONObject(latestIdx);
                        if (last != null) {
                            currentSessionId = UiUtils.optStr(last, "id");
                            currentPromptTokens = last.optInt("prompt_tokens", 0);
                            currentCompletionTokens = last.optInt("completion_tokens", 0);
                            currentCacheHitTokens = last.optInt("cache_hit_tokens", 0);
                            updateTokenStatsUI();
                            JSONArray msgs = readChatFile(currentSessionId);
                            messages = msgs == null ? new JSONArray() : msgs;
                        }
                    }
                    if (currentSessionId == null || currentSessionId.length() == 0) initNewSession();
                }
            }
            ensureSystemMessage();
            fixDanglingToolCalls();
            renderMessages();
            if (draftRestored) addStoppedRow(t("stopped_recover", "上次生成被中断，已保留已生成的内容"));
            applyChatBackground();
            setupListeners();

            applyRootInsets();
            applyDrawerInsets();
            setupKeyboardResize();

            restoreInputDraft();
        } catch (Throwable t) {
            Toast.makeText(this, t("err_init", "初始化失败: ") + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyUiTexts() {
        try {
            if (layoutWelcome != null && layoutWelcome.getChildCount() >= 3) {
                View v0 = layoutWelcome.getChildAt(0);
                if (v0 instanceof TextView) ((TextView) v0).setText(t("main_welcome_title", "你好，我能帮什么忙吗？"));
                View v1 = layoutWelcome.getChildAt(1);
                if (v1 instanceof TextView) ((TextView) v1).setText(t("main_welcome_sub1", "可以让我读写文件、联网搜索、处理照片"));
                View v2 = layoutWelcome.getChildAt(2);
                if (v2 instanceof TextView) ((TextView) v2).setText(t("main_welcome_sub2", "输入 @ 可以引用本地文件"));
            }
            if (etInput != null) etInput.setHint(t("main_input_hint", "给 AI Office 发送消息，@ 可引用文件"));
            if (btnSend != null && !isGenerating) btnSend.setText(t("main_send", "发送"));
            if (etSessionSearch != null) etSessionSearch.setHint(t("main_search_session", "搜索对话"));

            View btnMore = findViewById(R.id.btnMore);
            if (btnMore != null && btnMore.getParent() instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) btnMore.getParent();
                if (row.getChildCount() >= 1 && row.getChildAt(0) instanceof TextView) {
                    ((TextView) row.getChildAt(0)).setText(t("main_drawer_title", "对话记录"));
                }
            }
            View btnNewChat = findViewById(R.id.btnNewChat);
            if (btnNewChat instanceof TextView) ((TextView) btnNewChat).setText(t("main_new_chat", "+ 新建对话"));

            View llHist = findViewById(R.id.llHistoryList);
            if (llHist != null && llHist.getParent() instanceof View) {
                View sv = (View) llHist.getParent();
                if (sv.getParent() instanceof LinearLayout) {
                    LinearLayout panel = (LinearLayout) sv.getParent();
                    for (int i = 0; i < panel.getChildCount(); i++) {
                        View child = panel.getChildAt(i);
                        if (child instanceof TextView && child != btnNewChat && child != etSessionSearch) {
                            CharSequence cs = ((TextView) child).getText();
                            if (cs != null && cs.length() > 0) {
                                String s = cs.toString();
                                if (s.contains("长按") || s.contains("Tap") || s.contains("long-press")) {
                                    ((TextView) child).setText(t("main_drawer_hint", "点击切换会话，长按可重命名/删除/导出"));
                                }
                            }
                        }
                    }
                }
            }

            View btnRemoveImage = findViewById(R.id.btnRemoveImage);
            if (btnRemoveImage instanceof TextView) ((TextView) btnRemoveImage).setText(t("main_remove_image", "移除图片"));
        } catch (Throwable t) {}
    }

    private void applyThemeChrome() {
        try {
            int statusH = getSystemBarDimen("status_bar_height");
            View menu = findViewById(R.id.btnMenu);
            if (menu != null && menu.getParent() instanceof View) {
                View topbar = (View) menu.getParent();
                try {
                    android.view.ViewGroup.LayoutParams lp = topbar.getLayoutParams();
                    if (lp != null) {
                        lp.height = UiUtils.dp(this, 54) + statusH;
                        topbar.setLayoutParams(lp);
                    }
                } catch (Throwable t) {}
                topbar.setPadding(topbar.getPaddingLeft(), statusH,
                                  topbar.getPaddingRight(), topbar.getPaddingBottom());
                topbar.setBackgroundDrawable(UiOverrides.topbarBgDrawable(this));
            }
            View plus = findViewById(R.id.btnPlus);
            if (plus != null && plus.getParent() instanceof View) {
                View inputBar = (View) plus.getParent();
                inputBar.setBackgroundDrawable(UiOverrides.inputBarBgDrawable(this));
            }
            if (layoutDrawer != null && layoutDrawer.getChildCount() > 0) {
                View panel = layoutDrawer.getChildAt(0);
                if (panel != null) panel.setBackgroundDrawable(UiOverrides.drawerBgDrawable(this));
            }
            if (btnSend != null) {
                btnSend.setBackgroundDrawable(UiOverrides.sendBtnBgDrawable(this));
                btnSend.setTextColor(UiOverrides.sendBtnText(this));
            }
        } catch (Throwable t) {}
    }

@Override
protected void applySystemBars() {
    try {
        boolean night = UiUtils.isNight(this);
        try {
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                  | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        } catch (Throwable t) {}

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            View dv = getWindow().getDecorView();
            int flags = dv.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (night) flags = flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags = flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (night) flags = flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags = flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            dv.setSystemUiVisibility(flags);
        }

        // ★ API 30+：真正 edge-to-edge
        if (Build.VERSION.SDK_INT >= 30) {
            try { getWindow().setDecorFitsSystemWindows(false); } catch (Throwable t) {}
        }
    } catch (Throwable t) {}
}

    private void applyTopbarIcons() {
        try {
            int tint = UiOverrides.onSurface(this);
            View menu = findViewById(R.id.btnMenu);
            if (menu instanceof ImageView) ((ImageView) menu).setColorFilter(tint);
            View st = findViewById(R.id.btnSettings);
            if (st instanceof ImageView) ((ImageView) st).setColorFilter(tint);
            if (tvTitle != null) tvTitle.setTextColor(UiOverrides.onSurface(this));
        } catch (Throwable t) {}
    }
    
private void applyRootInsets() {
    final View root = findViewById(R.id.llMainRoot);
    if (root == null) return;
    // post 保证 root 已 attach 到窗口，insets 才是有效值
    root.post(new Runnable() {
        @Override public void run() {
            try {
                int gap = UiUtils.dp(MainActivity.this, 8);
                int[] bars = getSystemBarInsets(root);
                root.setPadding(bars[0], 0, bars[2], bars[3] + gap);
            } catch (Throwable t) {}
        }
    });
}

private int[] getSystemBarInsets(View root) {
    int L = 0, T = 0, R = 0, B = 0;
    boolean got = false;
    try {
        if (Build.VERSION.SDK_INT >= 30 && root != null) {
            android.view.WindowInsets ins = root.getRootWindowInsets();
            if (ins != null) {
                android.graphics.Insets bars = ins.getInsets(android.view.WindowInsets.Type.systemBars());
                L = bars.left; T = bars.top; R = bars.right; B = bars.bottom;
                got = (L > 0 || T > 0 || R > 0 || B > 0);
            }
        }
        if (!got && Build.VERSION.SDK_INT >= 20 && root != null) {
            android.view.WindowInsets ins = root.getRootWindowInsets();
            if (ins != null) {
                L = ins.getSystemWindowInsetLeft();
                T = ins.getSystemWindowInsetTop();
                R = ins.getSystemWindowInsetRight();
                B = ins.getSystemWindowInsetBottom();
                got = (L > 0 || T > 0 || R > 0 || B > 0);
            }
        }
    } catch (Throwable t) {}
    if (!got) {
        int navH = getSystemBarDimen("navigation_bar_height");
        int statH = getSystemBarDimen("status_bar_height");
        int rot = 0;
        try { rot = getWindowManager().getDefaultDisplay().getRotation(); } catch (Throwable t) {}
        if (rot == android.view.Surface.ROTATION_90) R = navH;
        else if (rot == android.view.Surface.ROTATION_270) L = navH;
        else B = navH;
        T = statH;
    }
    return new int[]{L, T, R, B};
}

private void setupKeyboardResize() {
    final View root = findViewById(R.id.llMainRoot);
    if (root == null) return;
    final int gap = UiUtils.dp(this, 8);
    root.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
        private int lastKb = 0;
        private int lastL = -1, lastR = -1, lastB = -1;
        @Override public void onGlobalLayout() {
            try {
                android.graphics.Rect r = new android.graphics.Rect();
                root.getWindowVisibleDisplayFrame(r);
                int screenH = root.getRootView().getHeight();
                int visibleBottom = r.bottom;
                int hidden = screenH - visibleBottom;

                int[] bars = getSystemBarInsets(root);
                int navL = bars[0], navR = bars[2], navB = bars[3];

                int kb = hidden - navB;
                if (kb < 100) kb = 0;

                int B = (kb > 0) ? (kb + gap) : (navB + gap);

                if (kb != lastKb || navL != lastL || navR != lastR || B != lastB) {
                    lastKb = kb; lastL = navL; lastR = navR; lastB = B;
                    root.setPadding(navL, 0, navR, B);
                }
            } catch (Throwable t) {}
        }
    });
}

    private void applyDrawerInsets() {
        try {
            View drawer = findViewById(R.id.layoutDrawer);
            if (!(drawer instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup vg = (android.view.ViewGroup) drawer;
            if (vg.getChildCount() == 0) return;
            View panel = vg.getChildAt(0);
            if (panel == null) return;
            int top = getSystemBarDimen("status_bar_height");
            panel.setPadding(panel.getPaddingLeft(),
                             panel.getPaddingTop() + top,
                             panel.getPaddingRight(),
                             panel.getPaddingBottom());
        } catch (Throwable t) {}
    }

    private int getSystemBarDimen(String name) {
        try {
            int resId = getResources().getIdentifier(name, "dimen", "android");
            if (resId > 0) return getResources().getDimensionPixelSize(resId);
        } catch (Throwable t) {}
        return 0;
    }

    @Override protected void onResume() {
        super.onResume();
        inForeground = true;
        Notifier.cancel(this);
        try { loadConfig(); } catch (Throwable t) {}
        try { refreshTools(); } catch (Throwable t) {}
        try { updateModelLabel(); } catch (Throwable t) {}
        try { applyChatBackground(); } catch (Throwable t) {}
        try { applyThemeChrome(); } catch (Throwable t) {}
        try { applyUiTexts(); } catch (Throwable t) {}
    }

private void refreshTools() {
    try {
        tools = AiClient.buildTools(
                UiUtils.getBool(this, "allow_shell_tool", false),
                UiUtils.getBool(this, "allow_accessibility_tool", false),
                true);
        try { PluginManager.registerTools(this, tools); } catch (Throwable t) {}
    } catch (Throwable t) {}
}

    @Override protected void onPause() {
        super.onPause();
        inForeground = false;
        try { saveCurrentSession(); } catch (Throwable t) {}
        try { saveInputDraft(); } catch (Throwable t) {}
    }

    @Override protected void onDestroy() {
        if (isFinishing()) {
            alive = false;
            try { if (aiClient != null) aiClient.cancel(); } catch (Throwable t) {}
            try { ui.removeCallbacksAndMessages(null); } catch (Throwable t) {}
            try { unregisterReceiver(bgRefreshReceiver); } catch (Throwable t) {}
        }
        try { saveCurrentSession(); } catch (Throwable t) {}
        try { saveInputDraft(); } catch (Throwable t) {}
        super.onDestroy();
    }

    private void saveInputDraft() {
        try {
            if (etInput == null) return;
            String txt = etInput.getText().toString();
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (txt == null || txt.length() == 0) p.edit().remove("input_draft").commit();
            else p.edit().putString("input_draft", txt).commit();
        } catch (Throwable t) {}
    }

    private void restoreInputDraft() {
        try {
            if (etInput == null) return;
            String txt = getSharedPreferences(PREFS, MODE_PRIVATE).getString("input_draft", "");
            if (txt != null && txt.length() > 0) {
                etInput.setText(txt);
                etInput.setSelection(txt.length());
            }
        } catch (Throwable t) {}
    }

    private void setupListeners() {
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!guardClick()) return;
                refreshHistoryList();
                showDrawer();
            }
        });
        findViewById(R.id.viewDrawerMask).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideDrawer(); }
        });
        findViewById(R.id.btnNewChat).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!guardClick()) return;
                if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
                saveCurrentSession(); initNewSession(); hideDrawer();
            }
        });
        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!guardClick()) return;
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.btnModel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showModelPicker(); }
        });
        findViewById(R.id.btnMore).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMoreMenu(); }
        });
        findViewById(R.id.btnPlus).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!guardClick()) return;
                showAttachMenu();
            }
        });
        findViewById(R.id.btnRemoveImage).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearPendingImage(); }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!guardClick()) return;
                if (isGenerating) onStopClicked();
                else onSendClicked();
            }
        });

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count == 1 && s.length() > 0 && s.charAt(start) == '@') {
                    etInput.post(new Runnable() { @Override public void run() { showFilePicker(); } });
                }
            }
            @Override public void afterTextChanged(Editable s) {
                try {
                    String txt = s == null ? "" : s.toString();
                    SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                    if (txt.length() == 0) p.edit().remove("input_draft").apply();
                    else p.edit().putString("input_draft", txt).apply();
                } catch (Throwable t) {}
            }
        });

        etSessionSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                sessionFilter = s == null ? "" : s.toString().trim().toLowerCase();
                refreshHistoryList();
            }
        });
    }

    private boolean guardClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_GUARD) return false;
        lastClickTime = now;
        return true;
    }

    private int maxToolRounds() { return UiUtils.getInt(this, "max_tool_rounds", 0); }
    private int retryTimes() { return Math.max(0, UiUtils.getInt(this, "retry_times", 2)); }
    private int compressRounds() { return Math.max(2, UiUtils.getInt(this, "compress_rounds", 10)); }
    private boolean autoCompress() { return UiUtils.getBool(this, "auto_compress", true); }
    private boolean notifyDoneEnabled() { return UiUtils.getBool(this, "notify_done", true); }

    private String thinkingEffort() {
        String v = UiUtils.getStr(this, "thinking_effort", "");
        return v == null ? "" : v.trim();
    }

    private void showDrawer() {
        try {
            layoutDrawer.setVisibility(View.VISIBLE);
            View content = layoutDrawer.getChildAt(0);
            if (content != null) {
                android.view.animation.TranslateAnimation ta = new android.view.animation.TranslateAnimation(
                        android.view.animation.Animation.RELATIVE_TO_SELF, -1f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
                ta.setDuration(220);
                content.startAnimation(ta);
            }
            android.view.animation.AlphaAnimation aa = new android.view.animation.AlphaAnimation(0f, 1f);
            aa.setDuration(220);
            layoutDrawer.startAnimation(aa);
        } catch (Throwable t) {
            try { layoutDrawer.setVisibility(View.VISIBLE); } catch (Throwable tt) {}
        }
    }

    private void hideDrawer() {
        try {
            final View content = layoutDrawer.getChildAt(0);
            if (content != null) {
                android.view.animation.TranslateAnimation ta = new android.view.animation.TranslateAnimation(
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, -1f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
                ta.setDuration(200);
                ta.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                    @Override public void onAnimationStart(android.view.animation.Animation a) {}
                    @Override public void onAnimationRepeat(android.view.animation.Animation a) {}
                    @Override public void onAnimationEnd(android.view.animation.Animation a) {
                        try { layoutDrawer.setVisibility(View.GONE); } catch (Throwable t) {}
                    }
                });
                content.startAnimation(ta);
            } else {
                layoutDrawer.setVisibility(View.GONE);
                return;
            }
            android.view.animation.AlphaAnimation aa = new android.view.animation.AlphaAnimation(1f, 0f);
            aa.setDuration(200);
            layoutDrawer.startAnimation(aa);
        } catch (Throwable t) {
            try { layoutDrawer.setVisibility(View.GONE); } catch (Throwable tt) {}
        }
    }

    private String[] modelPresets() {
        try {
            String raw = UiUtils.getStr(this, "model_presets", "deepseek-flash,deepseek-v4-pro");
            String[] parts = raw.split(",");
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < parts.length; i++) {
                String s = parts[i].trim();
                if (s.length() > 0 && !out.contains(s)) out.add(s);
            }
            String cur = UiUtils.getStr(this, "model", "deepseek-flash");
            if (cur.trim().length() > 0 && !out.contains(cur.trim())) out.add(cur.trim());
            return out.toArray(new String[out.size()]);
        } catch (Throwable t) { return new String[]{"deepseek-flash"}; }
    }

    private void updateModelLabel() {
        try {
            String m = UiUtils.getStr(this, "model", "");
            if (m == null) m = "";
            m = m.trim();
            if (m.length() == 0) m = "AI Office";
            boolean canSwitch = modelPresets().length > 1;
            if (tvTitle != null) tvTitle.setText(canSwitch ? (m + "  ▾") : m);
            if (tvModelName != null) tvModelName.setVisibility(View.GONE);
        } catch (Throwable t) {}
    }

private void showModelPicker() {
    try {
        final String[] models = modelPresets();
        if (models.length <= 1) return;
        final String editLabel = t("model_picker_go_settings", "去设置里编辑");

        String[] items = new String[models.length + 1];
        String[] trailing = new String[models.length + 1];
        System.arraycopy(models, 0, items, 0, models.length);
        items[models.length] = editLabel;

        String cur = UiUtils.getStr(this, "model", "");
        if (cur == null) cur = "";
        cur = cur.trim();
        for (int i = 0; i < models.length; i++) {
            trailing[i] = models[i].equals(cur) ? "✓" : "";
        }
        trailing[models.length] = "›";

        View anchor = findViewById(R.id.btnModel);

        GlassMenuDialog.showCompactItemsAt(this, anchor,
                t("model_picker_title", "选择模型"),
                items, trailing,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which < models.length) switchModel(models[which]);
                        else startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    }
                });
    } catch (Throwable t) {
        toast("model_no_picker", "无法打开模型列表");
    }
}

    private void switchModel(String model) {
        try {
            if (model == null || model.trim().length() == 0) return;
            UiUtils.prefs(this).edit().putString("model", model.trim()).apply();
            if (aiClient != null) aiClient.setModel(model.trim());
            updateModelLabel();
            Toast.makeText(this, t("model_switched", "已切换到 ") + model.trim(), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {}
    }

    private void requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3001);
            }
            if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()
                    && !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("asked_all_files", false)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("asked_all_files", true).apply();
                openAllFilesSettings();
            }
        } catch (Throwable t) {}
    }

    private void requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3002);
            }
        } catch (Throwable t) {}
    }

    public void requestScreenProjection() {
        try {
            ui.post(new Runnable() { @Override public void run() {
                try {
                    android.media.projection.MediaProjectionManager mpm =
                            (android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "无法发起截屏授权: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }});
        } catch (Throwable t) {}
    }

    private void openAllFilesSettings() {
        try {
            Intent it = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            it.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(it); } catch (Throwable t) { startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")); }
        } catch (Throwable t) { toast("permission_no_all_files", "无法打开权限设置页"); }
    }

    private boolean hasAllFilesAccess() {
        try { return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null); }
        catch (Throwable t) { return true; }
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String provider = prefs.getString("provider", "deepseek");
        String defUrl = AIProvider.urlOf(provider);
        if (defUrl == null || defUrl.length() == 0) defUrl = "https://api.deepseek.com";
        String baseUrl = prefs.getString("base_url", defUrl);
        if (baseUrl == null || baseUrl.trim().length() == 0) baseUrl = defUrl;
        String model = prefs.getString("model", "deepseek-flash");
        if (model == null || model.trim().length() == 0) model = "deepseek-flash";

        aiClient = new AiClient(baseUrl, prefs.getString("api_key", ""), model);

        String protocol = prefs.getString("protocol", "");
        if (protocol == null || protocol.trim().length() == 0) protocol = AIProvider.protocolOf(provider);
        try { aiClient.setProtocol(protocol); } catch (Throwable t) {}

        String vision = prefs.getString("vision_model", "");
        try { aiClient.setVisionModel(vision == null ? "" : vision.trim()); } catch (Throwable t) {}

        String vBase = prefs.getString("vision_base_url", "");
        String vKey  = prefs.getString("vision_api_key", "");
        String vProto = prefs.getString("vision_protocol", "");
        try {
            aiClient.setVisionEndpoint(
                    vBase == null ? "" : vBase,
                    vKey == null ? "" : vKey,
                    vProto == null ? "" : vProto);
        } catch (Throwable t) {}
    }

    private File chatDir() {
        File d = new File(getFilesDir(), "chats");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File chatFile(String id) { return new File(chatDir(), id + ".json"); }

    private static String readTextFile(File f, int maxBytes) {
        try {
            if (f == null || !f.exists()) return null;
            long len = f.length();
            if (len <= 0) return null;
            if (len > maxBytes) len = maxBytes;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) { int r = fis.read(buf, off, buf.length - off); if (r < 0) break; off += r; }
            try { fis.close(); } catch (Throwable t) {}
            return new String(buf, 0, off, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    private static void writeTextFile(File f, String text) {
        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(text.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {}
    }

    private void writeChatFile(String id, JSONArray msgs) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("messages", msgs);
            writeTextFile(chatFile(id), o.toString());
        } catch (Throwable t) {}
    }

    private JSONArray readChatFile(String id) {
        try {
            String txt = readTextFile(chatFile(id), 20 * 1024 * 1024);
            if (txt == null) return null;
            JSONObject o = new JSONObject(txt);
            return o.optJSONArray("messages");
        } catch (Throwable t) { return null; }
    }

    private void loadSessionIndex() {
        try {
            File f = new File(getFilesDir(), "sessions.json");
            String txt = readTextFile(f, 40 * 1024 * 1024);
            if (txt == null) return;
            JSONArray arr = new JSONArray(txt);
            JSONArray clean = new JSONArray();
            boolean migrated = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject raw = arr.optJSONObject(i);
                if (raw == null) continue;
                JSONObject s = new JSONObject(raw.toString());
                if (s.has("messages")) {
                    String id = UiUtils.optStr(s, "id");
                    if (id.length() == 0) id = "s" + System.currentTimeMillis() + "_" + i;
                    JSONArray msgs = s.optJSONArray("messages");
                    s.remove("messages");
                    s.put("id", id);
                    if (msgs != null) {
                        final String fid = id;
                        final JSONArray fmsgs = msgs;
                        io.execute(new Runnable() { @Override public void run() { writeChatFile(fid, fmsgs); } });
                        migrated = true;
                    }
                }
                clean.put(s);
            }
            sessionsJson = clean;
            if (migrated) persistSessionIndex();
        } catch (Throwable t) {
            sessionsJson = new JSONArray();
        }
    }

    private int findLatestSessionIndex() {
        try {
            int bestIdx = -1;
            long bestTime = -1;
            for (int i = 0; i < sessionsJson.length(); i++) {
                JSONObject s = sessionsJson.optJSONObject(i);
                if (s == null) continue;
                long t = s.optLong("time", 0);
                if (t > bestTime) {
                    bestTime = t;
                    bestIdx = i;
                }
            }
            return bestIdx;
        } catch (Throwable t) { return -1; }
    }

    private void persistSessionIndex() {
        try {
            final String txt = sessionsJson.toString();
            io.execute(new Runnable() { @Override public void run() { writeTextFile(new File(getFilesDir(), "sessions.json"), txt); } });
        } catch (Throwable t) {}
    }

    private void saveCurrentSession() {
        try {
            if (messages == null || messages.length() <= 1) return;
            final String sid = currentSessionId;
            if (sid == null || sid.length() == 0) return;
            final String snapshot = messages.toString();

            JSONObject entry = new JSONObject();
            entry.put("id", sid);
            entry.put("title", sessionTitle());
            entry.put("prompt_tokens", currentPromptTokens);
            entry.put("completion_tokens", currentCompletionTokens);
            entry.put("cache_hit_tokens", currentCacheHitTokens);
            entry.put("time", System.currentTimeMillis());
            int found = -1;
            for (int i = 0; i < sessionsJson.length(); i++) {
                JSONObject s = sessionsJson.optJSONObject(i);
                if (s != null && sid.equals(UiUtils.optStr(s, "id"))) { found = i; break; }
            }
            if (found >= 0) sessionsJson.put(found, entry); else sessionsJson.put(entry);
            persistSessionIndex();

            io.execute(new Runnable() {
                @Override public void run() {
                    try {
                        JSONArray arr = new JSONArray(snapshot);
                        sanitizeInPlace(arr);
                        writeChatFile(sid, arr);
                    } catch (Throwable t) {}
                }
            });
        } catch (Throwable t) {}
    }

    private void sanitizeInPlace(JSONArray arr) {
        try {
            File dir = new File(getFilesDir(), "chat_imgs");
            if (!dir.exists()) dir.mkdirs();
            String sid = currentSessionId == null ? "s" : currentSessionId;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                Object c = m.opt("content");
                if (!(c instanceof JSONArray)) continue;
                JSONArray parts = (JSONArray) c;
                boolean changed = false;
                for (int k = 0; k < parts.length(); k++) {
                    JSONObject p = parts.optJSONObject(k);
                    if (p == null || !"image_url".equals(p.optString("type"))) continue;
                    JSONObject u = p.optJSONObject("image_url");
                    if (u == null) continue;
                    String url = u.optString("url");
                    if (url.startsWith("ref:")) continue;
                    int b = url.indexOf("base64,");
                    if (b < 0) continue;
                    byte[] data;
                    try { data = Base64.decode(url.substring(b + 7), Base64.DEFAULT); } catch (Throwable t) { continue; }
                    File f = new File(dir, sid + "_m" + i + "_p" + k + ".jpg");
                    try {
                        FileOutputStream fo = new FileOutputStream(f);
                        fo.write(data);
                        fo.close();
                        u.put("url", "ref:" + f.getAbsolutePath());
                        changed = true;
                    } catch (Throwable t) {}
                }
                if (changed) m.put("content", parts);
            }
        } catch (Throwable t) {}
    }

    private void deleteChatImages(String sid) {
        try {
            if (sid == null || sid.length() == 0) return;
            File dir = new File(getFilesDir(), "chat_imgs");
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (int i = 0; i < fs.length; i++) {
                if (fs[i].getName().startsWith(sid + "_")) fs[i].delete();
            }
        } catch (Throwable t) {}
    }

    private String sessionTitle() {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m != null && "user".equals(UiUtils.optStr(m, "role"))) {
                    String t = UiUtils.contentToText(m, true).replace('\n', ' ').trim();
                    if (t.length() > 14) t = t.substring(0, 14) + "…";
                    return t.length() == 0 ? t("session_new_chat", "新对话") : t;
                }
            }
        } catch (Throwable t) {}
        return t("session_new_chat", "新对话");
    }

    private int countTurns() {
        int n = 0;
        try {
            boolean inUserTurn = false;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("user".equals(role)) { inUserTurn = true; n++; }
                else if ("assistant".equals(role) && inUserTurn) inUserTurn = false;
            }
        } catch (Throwable t) {}
        return n;
    }

    private void initNewSession() {
        messages = new JSONArray();
        currentSessionId = "s" + System.currentTimeMillis();
        currentPromptTokens = 0; currentCompletionTokens = 0; currentCacheHitTokens = 0;
        updateTokenStatsUI();
        renderLimit = MAX_RENDER_MESSAGES;
        ensureSystemMessage();
        resetRound();
        currentAiContainer = null; stoppedRow = null; clearPendingImage();
        pendingToolIds = new JSONArray(); discardToolResults = false;
        if (llMessages != null) llMessages.removeAllViews();
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.VISIBLE);
    }

    private void ensureSystemMessage() {
        try {
            String basePrompt = getSharedPreferences(PREFS, MODE_PRIVATE).getString("system_prompt", DEFAULT_SYSTEM_PROMPT);
            if (basePrompt == null || basePrompt.trim().length() == 0) basePrompt = DEFAULT_SYSTEM_PROMPT;

            String memoryBlock = "";
            try { memoryBlock = MemoryStore.buildMemoryBlock(this); } catch (Throwable t) {}

            StringBuilder caps = new StringBuilder();
            boolean shellOn = UiUtils.getBool(this, "allow_shell_tool", false);
            boolean a11yOn = UiUtils.getBool(this, "allow_accessibility_tool", false);
            if (shellOn) {
                caps.append("- run_shell_command：在本机执行 shell 命令（Android 无 Python，pip 不可用；先 which 探测解释器；脚本/调库用 run_js）\n");
                caps.append("- run_js：执行 JavaScript 并返回结果（__send(结果) 返回，可联网加载 JS 库）\n");
            }
            if (a11yOn) {
                if (AccessibilityController.isReady()) {
                    caps.append("- accessibility_control：操控手机。流程：launch_app 启动应用 → sleep 等加载 → screen 读屏 → click_text/tap/input/swipe 操作。"
                            + "若 screen 读不到控件（微信等限制读取），改用 action=screenshot 截屏（截图自动附图，带 10% 网格刻度），看图后按截图上的像素坐标返回 tap/long_press/swipe 并传 from_vision=true，App 自动换算成屏幕坐标。"
                            + "输入文字：input 读不到输入框时先 tap 聚焦再重试（自动降级剪贴板粘贴）。★ 视觉任务全部完成后必须调用 action=stop_projection 停止屏幕共享（关投屏、清通知）；另有 10 分钟无截屏自动停止兜底\n");
                } else {
                    caps.append("- accessibility_control：已开启但系统无障碍服务未连接。若用户需要操控手机，请引导：系统设置 → 无障碍 → AI Office「手机操控」→ 开启\n");
                }
            }

            String prompt = basePrompt;
            if (caps.length() > 0) prompt += "\n\n【本机高级能力（工具列表中真实可用）】\n" + caps;
            prompt += "\n\n" + memoryBlock;

            if (messages == null) messages = new JSONArray();
            JSONObject first = messages.length() > 0 ? messages.optJSONObject(0) : null;
            if (first != null && "system".equals(UiUtils.optStr(first, "role"))) first.put("content", prompt);
            else {
                JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", prompt);
                JSONArray n = new JSONArray(); n.put(sys);
                for (int i = 0; i < messages.length(); i++) n.put(messages.opt(i));
                messages = n;
            }
        } catch (Throwable t) {}
    }

    private void updateTokenStatsUI() {
        if (tvTokenStats != null) {
            String input = t("stats_input", "输入");
            String output = t("stats_output", "输出");
            String cache = t("stats_cache", "缓存命中");
            String turns = t("stats_turns", "轮次");
            String stats = input + ": " + currentPromptTokens + " | " + output + ": " + currentCompletionTokens;
            if (currentPromptTokens > 0) stats += " | " + cache + ": " + String.format("%.1f", (float) currentCacheHitTokens / currentPromptTokens * 100f) + "%";
            else stats += " | " + cache + ": 0%";
            stats += " | " + turns + ": " + countTurns();
            tvTokenStats.setText(stats);
        }
    }

    // ============================================================
    // 抽屉
    // ============================================================

    private void refreshHistoryList() {
        if (llHistoryList == null) return;
        llHistoryList.removeAllViews();

        int shown = 0;
        for (int i = sessionsJson.length() - 1; i >= 0; i--) {
            final int index = i;
            JSONObject s = sessionsJson.optJSONObject(i);
            if (s == null) continue;
            String title = UiUtils.optStr(s, "title");
            if (title.length() == 0) title = t("session_default_title", "对话");
            if (sessionFilter.length() > 0 && !title.toLowerCase().contains(sessionFilter)) continue;

            boolean isCurrent = currentSessionId.equals(UiUtils.optStr(s, "id"));

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 12),
                            UiUtils.dp(this, 14), UiUtils.dp(this, 12));
            if (UiOverrides.glassEnabled(this)) {
                item.setBackgroundDrawable(UiOverrides.glassItemBgDrawable(this, isCurrent));
            } else {
                if (isCurrent) item.setBackgroundResource(R.drawable.session_item_active_bg);
                else item.setBackgroundResource(R.drawable.session_item_bg);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(UiUtils.dp(this, 4), UiUtils.dp(this, 2),
                           UiUtils.dp(this, 4), UiUtils.dp(this, 2));
            item.setLayoutParams(ilp);

            TextView tv = new TextView(this);
            tv.setText(title);
            tv.setTextSize(14);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setTextColor(UiUtils.color(this, isCurrent ? R.color.md_primary : R.color.md_on_surface));
            if (isCurrent) tv.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(tv);

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchSession(index); }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { showSessionMenu(index); return true; }
            });
            llHistoryList.addView(item);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(sessionFilter.length() > 0
                    ? t("main_no_match_sessions", "没有匹配的对话")
                    : t("main_no_sessions", "暂无历史对话"));
            empty.setTextSize(13);
            empty.setTextColor(UiUtils.color(this, R.color.md_outline));
            empty.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 24),
                             UiUtils.dp(this, 10), UiUtils.dp(this, 10));
            llHistoryList.addView(empty);
        }
    }

    private void showSessionMenu(final int index) {
        final JSONObject s = sessionsJson.optJSONObject(index);
        if (s == null) return;
        final String[] items = new String[]{
                t("session_menu_switch", "切换到这个对话"),
                t("session_menu_rename", "重命名"),
                t("session_menu_export", "导出为 Markdown"),
                t("session_menu_share", "分享全文"),
                t("session_menu_delete", "删除")};
        GlassMenuDialog.showItems(this, UiUtils.optStr(s, "title"), items,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) switchSession(index);
                        else if (which == 1) renameSession(index);
                        else if (which == 2) exportSessionByIndex(index);
                        else if (which == 3) shareSessionByIndex(index);
                        else if (which == 4) confirmDeleteSession(index);
                    }
                });
    }

    private void renameSession(final int index) {
        final JSONObject s = sessionsJson.optJSONObject(index);
        if (s == null) return;
        final EditText et = new EditText(this);
        et.setText(UiUtils.optStr(s, "title"));
        et.setSelection(et.getText().length());
        et.setTextSize(15);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        new GlassDialog.Builder(this)
                .setTitle(t("session_rename_title", "重命名对话"))
                .setView(et)
                .setPositiveButton(t("common_save", "保存"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            String t2 = et.getText().toString().trim();
                            if (t2.length() == 0) t2 = t("session_default_title", "对话");
                            s.put("title", t2);
                            persistSessionIndex();
                            refreshHistoryList();
                        } catch (Throwable ex) {}
                        d.dismiss();
                    }
                })
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();
    }

    private void switchSession(int index) {
        if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
        saveCurrentSession();
        final JSONObject s = sessionsJson.optJSONObject(index);
        if (s == null) return;

        currentSessionId = UiUtils.optStr(s, "id");
        currentPromptTokens = s.optInt("prompt_tokens", 0);
        currentCompletionTokens = s.optInt("completion_tokens", 0);
        currentCacheHitTokens = s.optInt("cache_hit_tokens", 0);
        updateTokenStatsUI();
        renderLimit = MAX_RENDER_MESSAGES;
        resetRound();
        currentAiContainer = null; stoppedRow = null;
        pendingToolIds = new JSONArray(); discardToolResults = false;
        clearQuote();

        if (llMessages != null) {
            llMessages.removeAllViews();
            TextView loading = new TextView(this);
            loading.setText(t("session_loading", "加载中…"));
            loading.setTextSize(13);
            loading.setTextColor(UiUtils.color(this, R.color.md_outline));
            loading.setGravity(Gravity.CENTER);
            loading.setPadding(0, UiUtils.dp(this, 40), 0, 0);
            llMessages.addView(loading);
        }
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);

        hideDrawer();

        final String sid = currentSessionId;
        io.execute(new Runnable() {
            @Override public void run() {
                final JSONArray msgs = readChatFile(sid);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!alive) return;
                        messages = msgs == null ? new JSONArray() : msgs;
                        ensureSystemMessage();
                        fixDanglingToolCalls();
                        renderMessages();
                    }
                });
            }
        });
    }

    private void applyChatBackground() {
        try {
            View target = null;
            try { target = findViewById(R.id.llMainRoot); } catch (Throwable t) { target = null; }
            if (target == null) target = svMessages;

            if (svMessages != null) {
                try { svMessages.setBackgroundDrawable(null); } catch (Throwable t) {}
            }
            if (target == null) return;

String path = UiUtils.getStr(this, "chat_bg_path", "");
if (path == null || path.length() == 0) {
    // 无背景图：给一个柔和渐变，保证玻璃元素有"底"可透
    if (UiOverrides.glassEnabled(this)) {
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        UiUtils.isNight(this)
                            ? new int[]{0xFF1A1E2A, 0xFF0F1218, 0xFF1A1E2A}
                            : new int[]{0xFFF0F4FF, 0xFFE8EEF8, 0xFFF5F0FA});
        target.setBackgroundDrawable(gd);
        // 给 helper 一张渐变 Bitmap 做 backdrop source
        try {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            Bitmap grad = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(grad);
            gd.setBounds(0, 0, sw, sh);
            gd.draw(cv);
            BackdropBlurHelper.setScreenBackground(grad);
        } catch (Throwable t) {}
    } else {
        target.setBackgroundDrawable(null);
        BackdropBlurHelper.setScreenBackground(null);
    }
    return;
}

            Bitmap bmp;
            if (path.startsWith("content:") || path.startsWith("file:")) {
                bmp = null;
                try {
                    InputStream is = getContentResolver().openInputStream(Uri.parse(path));
                    if (is != null) {
                        try { bmp = BitmapFactory.decodeStream(is); } catch (Throwable t) { bmp = null; }
                        try { is.close(); } catch (Throwable t) {}
                    }
                } catch (Throwable t) { bmp = null; }
                if (bmp == null) {
                    UiUtils.prefs(this).edit().remove("chat_bg_path").commit();
                    toast("ref_image_invalid", "聊天背景图已失效，请到设置里重新选择");
                    target.setBackgroundDrawable(null);
                    return;
                }
            } else {
                bmp = decodeSampledFile(new File(path), 1080);
                if (bmp == null) {
                    UiUtils.prefs(this).edit().remove("chat_bg_path").commit();
                    target.setBackgroundDrawable(null);
                    return;
                }
            }

            if (UiOverrides.glassEnabled(this)) {
                Bitmap blurred = GlassBackdropHelper.fastBlur(bmp, 20);
                if (blurred != null && blurred != bmp) {
                    try { bmp.recycle(); } catch (Throwable t) {}
                    bmp = blurred;
                }
            }

android.graphics.drawable.BitmapDrawable bd =
        new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
bd.setAlpha(UiOverrides.glassEnabled(this) ? 255 : 180);
target.setBackgroundDrawable(bd);

// ★ 保存一份屏幕尺寸的清晰 Bitmap 供局部模糊使用
if (UiOverrides.glassEnabled(this)) {
    try {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        Bitmap screen = Bitmap.createScaledBitmap(bmp, sw, sh, true);
        BackdropBlurHelper.setScreenBackground(screen);
    } catch (Throwable t) {}
} else {
    BackdropBlurHelper.setScreenBackground(null);
}
        } catch (Throwable t) {}
    }

    private Bitmap decodeSampledFile(File f, int maxPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while ((o.outWidth / sample) > maxPx * 2 || (o.outHeight / sample) > maxPx * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) { return null; }
    }

    private void confirmDeleteSession(final int index) {
        new GlassDialog.Builder(this)
                .setTitle(t("session_delete_confirm", "删除这条对话记录？"))
                .setPositiveButton(t("common_delete", "删除"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            JSONObject s = sessionsJson.optJSONObject(index);
                            final String did = s == null ? "" : UiUtils.optStr(s, "id");
                            boolean isCurrent = did.equals(currentSessionId);
                            sessionsJson.remove(index);
                            persistSessionIndex();
                            if (did.length() > 0) {
                                final String fid = did;
                                io.execute(new Runnable() { @Override public void run() {
                                    try { chatFile(fid).delete(); } catch (Throwable t) {}
                                    try { deleteChatImages(fid); } catch (Throwable t) {}
                                }});
                            }
                            if (isCurrent) { currentSessionId = ""; messages = new JSONArray(); initNewSession(); }
                            refreshHistoryList();
                        } catch (Throwable t) {}
                        d.dismiss();
                    }
                })
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();
    }

    // ============================================================
    // 导出 / 分享
    // ============================================================

    private String messagesToMarkdown(JSONArray msgs, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null ? t("session_default_title", "对话") : title).append("\n\n");
        try {
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("system".equals(role)) continue;
                if ("user".equals(role)) {
                    sb.append("## ").append(t("md_role_me", "我")).append("\n\n")
                      .append(UiUtils.contentToText(m, true)).append("\n\n");
                } else if ("assistant".equals(role)) {
                    String r = UiUtils.optStr(m, "reasoning_content");
                    if (r.length() > 0) {
                        sb.append("<details><summary>").append(t("md_reasoning", "思考过程")).append("</summary>\n\n")
                          .append(r.replace("\n", "\n> ")).append("\n\n</details>\n\n");
                    }
                    String c = UiUtils.contentToText(m, true);
                    if (c.length() > 0) sb.append("## ").append(t("md_role_ai", "AI Office")).append("\n\n").append(c).append("\n\n");
                } else if ("tool".equals(role)) {
                    String content = UiUtils.optStr(m, "content");
                    if (content.length() > 4000) content = content.substring(0, 4000) + "\n...(已截断)";
                    String fence = content.contains("```") ? "~~~" : "```";
                    sb.append("<details><summary>").append(t("md_tool_result", "工具结果")).append("</summary>\n\n")
                      .append(fence).append("\n").append(content).append("\n").append(fence).append("\n\n</details>\n\n");
                }
            }
        } catch (Throwable t) {
            sb.append("## ").append(t("md_role_chat", "对话内容")).append("\n\n");
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("system".equals(role)) continue;
                if ("user".equals(role)) sb.append("### ").append(t("md_role_user_short", "用户")).append("\n").append(UiUtils.contentToText(m, true)).append("\n\n");
                else if ("assistant".equals(role)) sb.append("### ").append(t("md_role_ai_short", "AI")).append("\n").append(UiUtils.contentToText(m, true)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private void exportCurrentSession() {
        saveAndOfferShare(messagesToMarkdown(messages, sessionTitle()));
    }

    private void exportSessionByIndex(int index) {
        JSONObject s = sessionsJson.optJSONObject(index);
        if (s == null) return;
        JSONArray msgs = readChatFile(UiUtils.optStr(s, "id"));
        if (msgs == null) { toast("err_import_read", "读取会话内容失败"); return; }
        saveAndOfferShare(messagesToMarkdown(msgs, UiUtils.optStr(s, "title")));
    }

    private void shareSessionByIndex(int index) {
        JSONObject s = sessionsJson.optJSONObject(index);
        if (s == null) return;
        JSONArray msgs = readChatFile(UiUtils.optStr(s, "id"));
        if (msgs == null) { toast("err_import_read", "读取会话内容失败"); return; }
        shareText(messagesToMarkdown(msgs, UiUtils.optStr(s, "title")));
    }

    private void saveAndOfferShare(final String md) {
        if (md == null || md.trim().length() == 0) { toast("no_export_content", "没有可导出的内容"); return; }
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "AI/exports");
            if (!dir.exists()) dir.mkdirs();
            final File f = new File(dir, "ai_" + System.currentTimeMillis() + ".md");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(md.getBytes("UTF-8"));
            fos.close();
            new GlassDialog.Builder(this)
                    .setTitle(t("export_done", "已导出"))
                    .setMessage(f.getAbsolutePath())
                    .setPositiveButton(t("common_share", "分享"), new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) { shareText(md); d.dismiss(); }
                    })
                    .setNegativeButton(t("common_know", "知道了"), null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, t("export_fail", "导出失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareText(String text) {
        if (text == null || text.trim().length() == 0) { toast("no_share_content", "没有可分享的内容"); return; }
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("text/plain");
            it.putExtra(Intent.EXTRA_SUBJECT, sessionTitle());
            it.putExtra(Intent.EXTRA_TEXT, text);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(it, t("share_to", "分享到")));
        } catch (Throwable t) {
            Toast.makeText(this, t("share_fail", "分享失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // 消息渲染
    // ============================================================

    private void renderMessages() {
        renderMessagesOptimized();
    }

    private void appendUserBubbleIncremental() {
        try {
            if (llMessages == null || messages == null || messages.length() == 0) return;
            if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);
            addUserBubble(messages.length() - 1, messages.optJSONObject(messages.length() - 1));
        } catch (Throwable t) {}
    }

    private void appendVisionShot(String b64) {
        try {
            String sizeInfo = "";
            try {
                int vw = ProjectionController.visionImageWidth(), vh = ProjectionController.visionImageHeight();
                if (vw > 0 && vh > 0) sizeInfo = "截图像素尺寸 " + vw + "×" + vh
                        + "，图上叠加了 10% 网格线与边缘像素刻度，请尽量按刻度读坐标（不要凭比例目测）。";
            } catch (Throwable t) {}
            JSONArray arr = new JSONArray();
            JSONObject t2 = new JSONObject();
            t2.put("type", "text");
            t2.put("text", "【系统自动附图】这是当前屏幕截图。" + sizeInfo
                    + "请分析界面并决定下一步：tap/long_press/swipe（坐标直接用截图上的像素坐标并传 from_vision=true，"
                    + "App 会自动换算成屏幕坐标）、input（输入文字）、click_text（点已知文字）或 launch_app。");
            arr.put(t2);
            JSONObject im = new JSONObject();
            im.put("type", "image_url");
            JSONObject u = new JSONObject();
            u.put("url", "data:image/jpeg;base64," + b64);
            im.put("image_url", u);
            arr.put(im);
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", arr);
            messages.put(m);
            if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);
            addUserBubble(messages.length() - 1, m);
            scrollToBottom();
        } catch (Throwable t) {}
    }
    
    private void appendGeneratedImage(String b64) {
    try {
        JSONArray arr = new JSONArray();
        JSONObject t2 = new JSONObject();
        t2.put("type", "text");
        t2.put("text", "【系统自动附图】这是刚刚由 generate_image 工具生成的图片，请查看并向用户描述或继续对话。");
        arr.put(t2);
        JSONObject im = new JSONObject();
        im.put("type", "image_url");
        JSONObject u = new JSONObject();
        u.put("url", "data:image/jpeg;base64," + b64);
        im.put("image_url", u);
        arr.put(im);
        JSONObject m = new JSONObject();
        m.put("role", "user");
        m.put("content", arr);
        messages.put(m);
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);
        addUserBubble(messages.length() - 1, m);
        scrollToBottom();
    } catch (Throwable t) {}
}

private LinearLayout newAiBox(int msgIndex) {
    final LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 6),
                   UiUtils.dp(this, 4), UiUtils.dp(this, 6));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, UiUtils.dp(this, 6), 0, UiUtils.dp(this, 6));
    box.setLayoutParams(lp);
    try {
        android.graphics.drawable.Drawable bg = UiOverrides.bubbleAiBgDrawable(this);
        if (bg != null) {
            box.setBackgroundDrawable(bg);
            box.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8),
                           UiUtils.dp(this, 10), UiUtils.dp(this, 8));
        }
    } catch (Throwable t) {}
    try { box.setTag(Integer.valueOf(msgIndex)); } catch (Throwable t) {}
    llMessages.addView(box);

    if (UiOverrides.glassEnabled(this)) {
        box.post(new Runnable() {
            @Override public void run() {
                try {
                    android.graphics.drawable.Drawable d = UiOverrides.bubbleAiBgDrawable(MainActivity.this, box);
                    if (d != null) {
                        box.setBackgroundDrawable(d);
                        box.setPadding(UiUtils.dp(MainActivity.this, 10), UiUtils.dp(MainActivity.this, 8),
                                       UiUtils.dp(MainActivity.this, 10), UiUtils.dp(MainActivity.this, 8));
                    }
                } catch (Throwable t) {}
            }
        });
    }
    return box;
}

    private void addAiActionRow(final LinearLayout container, final int msgIndex) {
        if (container == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(UiUtils.dp(this, 2), UiUtils.dp(this, 2), UiUtils.dp(this, 2), UiUtils.dp(this, 2));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 4));
        row.setLayoutParams(rlp);
        String[] labels = new String[]{
                t("action_row_quote", "↩ 引用追问"),
                t("action_row_speak", "▶ 朗读"),
                t("action_row_copy", "⧉ 复制"),
                t("action_row_regen", "↻ 重新生成"),
                t("action_row_more", "⋯ 更多")};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView b = new TextView(this);
            b.setText(labels[i]);
            b.setTextSize(12);
            b.setTextColor(UiOverrides.outline(this));
            b.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 4), UiUtils.dp(this, 10), UiUtils.dp(this, 4));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) blp.setMargins(UiUtils.dp(this, 6), 0, 0, 0);
            b.setLayoutParams(blp);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                if (which == 0) startQuote(msgIndex);
                else if (which == 1) speakMessage(msgIndex);
                else if (which == 2) {
                    JSONObject m = (msgIndex >= 0 && msgIndex < messages.length()) ? messages.optJSONObject(msgIndex) : null;
                    if (m != null) MarkdownView.copyText(MainActivity.this, UiUtils.contentToText(m, true));
                }
                else if (which == 3) { if (!isGenerating) regenerateFrom(msgIndex); else toast("generating_short", "正在生成中"); }
                else if (which == 4) showAiMenu(msgIndex);
            }});
            row.addView(b);
        }
        try {
            List<JSONObject> uiPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_MESSAGE_LONG_PRESS);
            for (int k = 0; k < uiPlugins.size(); k++) {
                final JSONObject p = uiPlugins.get(k);
                String title = p.optString("title", p.optString("name", "插件"));
                TextView b = new TextView(this);
                b.setText(title);
                b.setTextSize(12);
                b.setTextColor(UiOverrides.outline(this));
                b.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 4), UiUtils.dp(this, 10), UiUtils.dp(this, 4));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                blp.setMargins(UiUtils.dp(this, 6), 0, 0, 0);
                b.setLayoutParams(blp);
                b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                    UiPluginDialog.show(MainActivity.this, p, new UiPluginDialog.OnSubmitListener() {
                        @Override public void onSubmit(final JSONObject args) {
                            runUiPluginInline(p, args);
                        }
                    });
                }});
                row.addView(b);
            }
        } catch (Throwable t) {}
        container.addView(row);
    }

    private void speakMessage(final int msgIndex) {
        try {
            if (msgIndex < 0 || msgIndex >= messages.length()) return;
            JSONObject m = messages.optJSONObject(msgIndex);
            if (m == null) return;
            final String text = UiUtils.contentToText(m, true);
            if (text == null || text.trim().length() == 0) { toast("tts_no_content", "这条回复没有可朗读的内容"); return; }
            if (TtsHelper.isSpeaking()) { TtsHelper.stop(); toast("tts_stopped", "已停止朗读"); return; }
            if (!TtsHelper.isEnabled(this)) {
                new GlassDialog.Builder(this)
                        .setTitle(t("tts_not_enabled_title", "TTS 未启用"))
                        .setMessage(t("tts_not_enabled_msg", "请先在「设置 → 语音合成」里开启 TTS 朗读。\n\n未设置 API 地址时会自动使用系统 TTS。"))
                        .setPositiveButton(t("tts_go_enable", "去开启"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    Intent it = new Intent(MainActivity.this, SettingsDetailActivity.class);
                                    it.putExtra(SettingsDetailActivity.EXTRA_CATEGORY, "tts");
                                    startActivity(it);
                                } catch (Throwable t) {}
                                d.dismiss();
                            }
                        })
                        .setNegativeButton(t("common_cancel", "取消"), null)
                        .show();
                return;
            }
            Toast.makeText(this, t("tts_start", "开始朗读…"), Toast.LENGTH_SHORT).show();
            TtsHelper.speak(this, text, new TtsHelper.TtsCallback() {
                @Override public void onStart() {}
                @Override public void onSuccess() {}
                @Override public void onError(final String message) {
                    ui.post(new Runnable() { @Override public void run() {
                        Toast.makeText(MainActivity.this, t("tts_fail", "朗读失败: ") + message, Toast.LENGTH_SHORT).show();
                    }});
                }
            });
        } catch (Throwable t) {}
    }

    private void startQuote(final int msgIndex) {
        try {
            if (msgIndex < 0 || msgIndex >= messages.length()) return;
            JSONObject m = messages.optJSONObject(msgIndex);
            if (m == null) return;
            String c = UiUtils.contentToText(m, true);
            if (c.trim().length() == 0) { toast("quote_no_text", "这条回复没有文字内容"); return; }
            if (c.length() > 2000) c = c.substring(0, 2000) + "…";
            quoteIndex = msgIndex;
            quoteText = c;
            if (tvQuoteText != null) {
                String prev = c.replace('\n', ' ');
                if (prev.length() > 60) prev = prev.substring(0, 60) + "…";
                String prefix = t("quote_prefix", "↩ 引用 #%d：").replace("%d", String.valueOf(msgIndex));
                tvQuoteText.setText(prefix + prev);
            }
            if (llQuoteBar != null) llQuoteBar.setVisibility(View.VISIBLE);
            if (etInput != null) etInput.requestFocus();
        } catch (Throwable t) {}
    }

    private void clearQuote() {
        quoteIndex = -1;
        quoteText = "";
        if (llQuoteBar != null) llQuoteBar.setVisibility(View.GONE);
        if (tvQuoteText != null) tvQuoteText.setText("");
    }

    private String applyQuoteIfAny(String text) {
        if (quoteIndex < 0 || quoteText.length() == 0) return text;
        StringBuilder sb = new StringBuilder();
        sb.append("【引用你之前的回复（第 ").append(quoteIndex).append(" 条），请结合它回答下面的新问题】\n")
          .append(quoteText).append("\n【引用结束】\n\n");
        if (text != null && text.length() > 0) sb.append(text);
        return sb.toString();
    }

private void addUserBubble(final int index, JSONObject m) {
    if (m == null) return;

    int screenWidth = getResources().getDisplayMetrics().widthPixels;
    int maxBubbleWidth = screenWidth - UiUtils.dp(this, 80);

    final LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.RIGHT);

    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    blp.setMargins(0, UiUtils.dp(this, 6), 0, UiUtils.dp(this, 6));
    box.setLayoutParams(blp);

    final android.graphics.drawable.Drawable bubbleBg = UiOverrides.bubbleUserBgDrawable(this);
    final int bubbleTextColor = UiOverrides.bubbleUserText(this);

    List<String> urls = UiUtils.extractImageUrls(m);
    for (int i = 0; i < urls.size() && i < 6; i++) {
        ImageView iv = new ImageView(this);
        int size = UiUtils.dp(this, 150);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
        ilp.gravity = Gravity.RIGHT;
        iv.setLayoutParams(ilp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (bubbleBg != null) iv.setBackgroundDrawable(bubbleBg);
        loadImageSmart(urls.get(i), iv);
        final String fUrl = urls.get(i);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showImageDialog(fUrl); }
        });
        box.addView(iv);
    }

    String text = UiUtils.contentToText(m, false);
    if (text.length() > 0) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setLineSpacing(UiUtils.dp(this, 3), 1f);
        tv.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 10),
                      UiUtils.dp(this, 14), UiUtils.dp(this, 10));
        tv.setTextColor(bubbleTextColor);
        if (bubbleBg != null) tv.setBackgroundDrawable(bubbleBg);
        else tv.setBackgroundResource(R.drawable.bubble_user_bg);
        tv.setSingleLine(false);
        tv.setMaxLines(50);
        tv.setEllipsize(null);
        tv.setHorizontallyScrolling(false);
        tv.setMaxWidth(maxBubbleWidth);

        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvlp.gravity = Gravity.RIGHT;
        tv.setLayoutParams(tvlp);
        box.addView(tv);
    }

    box.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) { showEditDialog(index); }
    });
    box.setOnLongClickListener(new View.OnLongClickListener() {
        @Override public boolean onLongClick(View v) { showUserMenu(index); return true; }
    });
    llMessages.addView(box);
    scrollToBottom();

    // ★ 布局完成后应用局部模糊玻璃背景
    if (UiOverrides.glassEnabled(this)) {
        box.post(new Runnable() {
            @Override public void run() {
                try {
                    android.graphics.drawable.Drawable d = UiOverrides.bubbleUserBgDrawable(MainActivity.this, box);
                    if (d == null) return;
                    for (int i = 0; i < box.getChildCount(); i++) {
                        View ch = box.getChildAt(i);
                        if (ch instanceof ImageView) continue;
                        ch.setBackgroundDrawable(d);
                    }
                } catch (Throwable t) {}
            }
        });
    }
}

    private void loadImageSmart(String url, ImageView iv) {
        if (url == null || iv == null) return;
        String path = UiUtils.refImagePath(url);
        if (path != null) loadFileThumbnail(path, iv);
        else {
            int k = url.indexOf("base64,");
            loadThumbnail(k >= 0 ? url.substring(k + 7) : url, iv);
        }
    }

    private void loadFileThumbnail(final String path, final ImageView iv) {
        new Thread(new Runnable() { @Override public void run() {
            Bitmap bm = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int sample = 1; while (o.outWidth > 0 && (o.outWidth / sample) > 480) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
                bm = BitmapFactory.decodeFile(path, o2);
            } catch (Throwable t) { bm = null; }
            if (bm == null) return;
            final Bitmap fb = bm;
            ui.post(new Runnable() { @Override public void run() {
                if (!alive || iv == null) { try { fb.recycle(); } catch (Throwable t) {} return; }
                try { iv.setImageBitmap(fb); } catch (Throwable t) {}
            }});
        }}).start();
    }

    private void showUserMenu(final int index) {
        if (index < 0 || index >= messages.length()) return;
        JSONObject m = messages.optJSONObject(index);
        if (m == null) return;
        boolean hasImg = UiUtils.extractImageUrls(m).size() > 0;
        List<String> items = new ArrayList<String>();
        items.add(t("msg_menu_edit", "修改并重新发送"));
        items.add(t("msg_menu_copy", "复制文字"));
        if (hasImg) items.add(t("msg_menu_view_image", "查看图片"));
        items.add(t("msg_menu_delete", "删除这条及之后"));
        final List<JSONObject> uiPlugins;
        try {
            uiPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_MESSAGE_LONG_PRESS);
        } catch (Throwable t) {
            uiPlugins = new ArrayList<JSONObject>();
        }
        for (int k = 0; k < uiPlugins.size(); k++) {
            JSONObject p = uiPlugins.get(k);
            items.add(p.optString("title", p.optString("name", "插件")));
        }
        final List<String> fItems = items;
        GlassMenuDialog.showItems(this, t("msg_menu_title", "这条消息"), fItems.toArray(new String[0]),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        String it = fItems.get(which);
                        if (t("msg_menu_edit", "修改并重新发送").equals(it)) showEditDialog(index);
                        else if (t("msg_menu_copy", "复制文字").equals(it)) {
                            JSONObject mm = messages.optJSONObject(index);
                            if (mm != null) MarkdownView.copyText(MainActivity.this, UiUtils.contentToText(mm, false));
                        }
                        else if (t("msg_menu_view_image", "查看图片").equals(it)) {
                            JSONObject mm = messages.optJSONObject(index);
                            List<String> us = mm == null ? new ArrayList<String>() : UiUtils.extractImageUrls(mm);
                            if (!us.isEmpty()) showImageDialog(us.get(0));
                        }
                        else if (t("msg_menu_delete", "删除这条及之后").equals(it)) confirmDeleteFrom(index);
                        else if (it != null) {
                            for (int k = 0; k < uiPlugins.size(); k++) {
                                JSONObject p = uiPlugins.get(k);
                                if (it.equals(p.optString("title", p.optString("name", "插件")))) {
                                    final JSONObject fp = p;
                                    UiPluginDialog.show(MainActivity.this, fp, new UiPluginDialog.OnSubmitListener() {
                                        @Override public void onSubmit(final JSONObject args) {
                                            runUiPluginInline(fp, args);
                                        }
                                    });
                                    break;
                                }
                            }
                        }
                    }
                });
    }

    private void confirmDeleteFrom(final int index) {
        new GlassDialog.Builder(this)
                .setTitle(t("msg_delete_confirm", "删除这条消息及其之后的所有内容？"))
                .setPositiveButton(t("common_delete", "删除"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        try {
                            while (messages.length() > index) messages.remove(messages.length() - 1);
                            removeStoppedRow();
                            resetRound(); renderMessages(); saveCurrentSession();
                        } catch (Throwable t) {}
                    }
                })
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();
    }

    private void showImageDialog(final String url) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(0xFF000000);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 24), UiUtils.dp(this, 8), UiUtils.dp(this, 24));
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wrap.addView(iv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView tip = new TextView(this);
        tip.setText(t("image_dialog_hint", "点击任意处关闭 · 长按图片保存到 AI/exports"));
        tip.setTextSize(12); tip.setTextColor(0xAAFFFFFF); tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, UiUtils.dp(this, 8), 0, 0);
        wrap.addView(tip);
        new Thread(new Runnable() { @Override public void run() {
            Bitmap bm = null;
            try {
                String path = UiUtils.refImagePath(url);
                if (path != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, o);
                    int sample = 1; while (o.outWidth > 0 && (o.outWidth / sample) > 2048) sample *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
                    bm = BitmapFactory.decodeFile(path, o2);
                } else {
                    int k = url.indexOf("base64,");
                    if (k >= 0) {
                        byte[] data = Base64.decode(url.substring(k + 7), Base64.DEFAULT);
                        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, o);
                        int sample = 1; while (o.outWidth > 0 && (o.outWidth / sample) > 2048) sample *= 2;
                        BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
                        bm = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
                    }
                }
            } catch (Throwable t) { bm = null; }
            if (bm == null) return;
            final Bitmap fb = bm;
            ui.post(new Runnable() { @Override public void run() {
                try { iv.setImageBitmap(fb); } catch (Throwable t) {}
            }});
        }}).start();
        iv.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            saveImageToExports(url);
            return true;
        }});
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(wrap);
        wrap.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { dlg.dismiss(); } catch (Throwable t) {} } });
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
            try { w.setDimAmount(0.8f); } catch (Throwable t) {}
        }
        try { dlg.show(); } catch (Throwable t) {}
    }

    private void saveImageToExports(String url) {
        try {
            Bitmap bm = null;
            String path = UiUtils.refImagePath(url);
            if (path != null) bm = BitmapFactory.decodeFile(path);
            else {
                int k = url.indexOf("base64,");
                if (k >= 0) {
                    byte[] data = Base64.decode(url.substring(k + 7), Base64.DEFAULT);
                    bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                }
            }
            if (bm == null) { toast("image_decode_fail", "图片解码失败"); return; }
            File dir = new File(Environment.getExternalStorageDirectory(), "AI/exports");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fo = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            Toast.makeText(this, t("image_saved", "已保存: ") + f.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, t("image_save_fail", "保存失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addMarkdownBox(LinearLayout container, String text) {
        if (container == null) return;
        try {
            View v = MarkdownView.render(this, text, 15f);
            container.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } catch (Throwable t) {
            TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(15);
            tv.setTextColor(UiOverrides.bubbleAiText(this)); tv.setTextIsSelectable(true); container.addView(tv);
        }
    }

    private void addReasoningPanel(final LinearLayout container, String text, long seconds) {
        if (container == null) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundDrawable(UiOverrides.codeBgDrawable(this));
        panel.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 6)); panel.setLayoutParams(lp);
        String headerText = seconds > 0
                ? t("reasoning_done_sec", "已深度思考（%d 秒）").replace("%d", String.valueOf(seconds))
                : t("reasoning_done", "已深度思考");
        TextView header = new TextView(this); header.setText(headerText);
        header.setTextSize(12); header.setTextColor(UiOverrides.outline(this));
        final TextView content = new TextView(this); content.setText(text); content.setTextSize(13); content.setLineSpacing(UiUtils.dp(this, 3), 1f);
        content.setTextColor(UiOverrides.bubbleAiText(this)); content.setTextIsSelectable(true); content.setPadding(0, UiUtils.dp(this, 6), 0, 0); content.setVisibility(View.GONE);
        header.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { content.setVisibility(content.getVisibility() == View.GONE ? View.VISIBLE : View.GONE); } });
        panel.addView(header); panel.addView(content); container.addView(panel);
    }

    private void addToolPanel(LinearLayout container, String toolName, String body, boolean showBody) {
        if (container == null) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundDrawable(UiOverrides.codeBgDrawable(this));
        panel.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 6)); panel.setLayoutParams(lp);
        final String callPrefix = t("tool_panel_call", "调用工具 ");
        final String resultPrefix = t("tool_panel_result", "工具结果 ");
        final String collapseSuffix = t("tool_panel_collapse", "（点击收起）");
        final TextView header = new TextView(this); header.setText(callPrefix + toolName);
        header.setTextSize(12); header.setTextColor(UiOverrides.outline(this));
        final TextView content = new TextView(this); content.setText(body == null ? "" : body); content.setTextSize(12); content.setTypeface(Typeface.MONOSPACE);
        content.setTextColor(UiOverrides.codeText(this)); content.setTextIsSelectable(true); content.setPadding(0, UiUtils.dp(this, 6), 0, 0); content.setVisibility(View.GONE);
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = content.getVisibility() == View.GONE;
                content.setVisibility(show ? View.VISIBLE : View.GONE);
                String cur = header.getText().toString();
                if (show) {
                    if (cur.startsWith(callPrefix)) header.setText(cur.replace(callPrefix, resultPrefix));
                } else {
                    if (cur.startsWith(resultPrefix)) header.setText(cur.replace(resultPrefix, callPrefix));
                }
            }
        });
        panel.addView(header); panel.addView(content); container.addView(panel);
        if (showBody) { content.setVisibility(View.VISIBLE); header.setText(resultPrefix + toolName + collapseSuffix); }
    }

    private void addSystemNote(String msg) {
        TextView tv = new TextView(this); tv.setText(msg); tv.setTextSize(12); tv.setTextColor(UiOverrides.error(this));
        tv.setGravity(Gravity.CENTER); tv.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 8), UiUtils.dp(this, 8), UiUtils.dp(this, 8)); llMessages.addView(tv); scrollToBottom();
    }

    private void showAiMenu(final int assistantIndex) {
        if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
        if (assistantIndex < 0 || assistantIndex >= messages.length()) return;
        JSONObject m = messages.optJSONObject(assistantIndex);
        if (m == null) return;
        final String full = UiUtils.contentToText(m, true);
        final String reasoning = UiUtils.optStr(m, "reasoning_content");
        final boolean hasText = full.trim().length() > 0;

        String[] items;
        if (hasText) items = new String[]{
                t("ai_menu_copy_reply", "复制这条回复"),
                t("ai_menu_copy_reasoning", "复制思考过程"),
                t("ai_menu_share", "分享这条回复"),
                t("ai_menu_regen", "从这里重新生成"),
                t("ai_menu_delete_round", "删除这一轮")};
        else items = new String[]{
                t("ai_menu_copy_reasoning", "复制思考过程"),
                t("ai_menu_regen", "从这里重新生成"),
                t("ai_menu_delete_round", "删除这一轮")};

        GlassMenuDialog.showItems(this, t("ai_menu_title", "这条回复"), items,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        handleAiMenu(assistantIndex, full, reasoning, which, hasText);
                    }
                });
    }

    private void handleAiMenu(int idx, String full, String reasoning, int which, boolean hasText) {
        if (hasText) {
            if (which == 0) { MarkdownView.copyText(this, full); return; }
            if (which == 1) { copyReasoning(reasoning); return; }
            if (which == 2) { shareText(full); return; }
            if (which == 3) { regenerateFrom(idx); return; }
            if (which == 4) { deleteRoundFrom(idx); return; }
        } else {
            if (which == 0) { copyReasoning(reasoning); return; }
            if (which == 1) { regenerateFrom(idx); return; }
            if (which == 2) { deleteRoundFrom(idx); return; }
        }
    }

    private void copyReasoning(String reasoning) {
        if (reasoning == null || reasoning.trim().length() == 0) { toast("no_reasoning", "这条回复没有思考过程"); return; }
        MarkdownView.copyText(this, reasoning);
    }

    private int findTurnStart(int assistantIndex) {
        try {
            for (int i = assistantIndex; i >= 1; i--) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                if ("user".equals(UiUtils.optStr(m, "role"))) return i + 1;
            }
        } catch (Throwable t) {}
        return 1;
    }

    private void regenerateFrom(final int assistantIndex) {
        try {
            int turnStart = findTurnStart(assistantIndex);
            while (messages.length() > turnStart) messages.remove(messages.length() - 1);
            removeStoppedRow();
            resetRound(); renderMessages();
            if (messages.length() <= 1) { toast("no_context", "没有可用的上下文"); return; }
            startAgent();
        } catch (Throwable t) {}
    }

    private void deleteRoundFrom(final int assistantIndex) {
        try {
            int turnStart = findTurnStart(assistantIndex);
            while (messages.length() > turnStart) messages.remove(messages.length() - 1);
            removeStoppedRow();
            resetRound(); renderMessages(); saveCurrentSession();
            toast("round_deleted", "已删除该轮回复");
        } catch (Throwable t) {}
    }

    // ============================================================
    // 发送 / 停止 / 继续 / 重新生成
    // ============================================================

    private void onSendClicked() {
        if (isGenerating) return;
        String text = etInput.getText().toString().trim();
        if (text.length() == 0 && pendingImageBase64List.isEmpty()) { toast("input_required", "请输入内容"); return; }

        setGeneratingState(true);
        etInput.setText(""); removeStoppedRow();
        try { getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("input_draft").apply(); } catch (Throwable t) {}

        if (quoteIndex >= 0) {
            text = applyQuoteIfAny(text);
            clearQuote();
        }

        text = expandAtReferences(text);

        List<String> b64List = new ArrayList<String>(pendingImageBase64List);
        List<byte[]> bytesList = new ArrayList<byte[]>(pendingImageBytesList);
        clearPendingImage();

        if (UiUtils.getBool(this, "image_as_file", false) && !bytesList.isEmpty()) {
            StringBuilder pb = new StringBuilder(text);
            for (int i = 0; i < bytesList.size(); i++) {
                String path = saveImageToFile(bytesList.get(i));
                if (path != null) {
                    if (pb.length() > 0) pb.append('\n');
                    pb.append("【用户附带了一张图片，已保存到本地文件】").append(path);
                }
            }
            putUserMessage(pb.toString(), null);
        } else {
            putUserMessage(text, b64List.isEmpty() ? null : b64List);
        }
        appendUserBubbleIncremental();
        saveCurrentSession();

        shouldStop = false;
        discardToolResults = false;
        clearDraft();
        fixDanglingToolCalls();
        runAgentStep(0);
    }

    private String expandAtReferences(String text) {
        if (text == null) return "";
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("@([^\\s@，。；、]+)").matcher(text);
            List<String> added = new ArrayList<String>();
            while (m.find()) {
                String p = m.group(1);
                File f = resolveUserPath(p);
                if (f == null || !f.exists() || f.isDirectory()) continue;
                String content = fileExecutor.readTextForUi(f, AT_FILE_MAX);
                if (content == null) continue;
                added.add("【引用文件】" + f.getAbsolutePath() + "\n```\n" + content + "\n```");
            }
            if (added.size() == 0) return text;
            StringBuilder sb = new StringBuilder(text);
            sb.append("\n\n");
            for (int i = 0; i < added.size(); i++) sb.append(added.get(i)).append("\n\n");
            return sb.toString();
        } catch (Throwable t) { return text; }
    }

    private File resolveUserPath(String p) {
        try {
            if (p == null) return null;
            p = p.trim();
            if (p.length() == 0) return null;
            if (p.startsWith("~")) return new File(getFilesDir(), p.substring(1));
            if (p.startsWith("/")) return new File(p);
            File a = new File(fileExecutor.getWorkspace(), p);
            if (a.exists()) return a;
            File b = new File(Environment.getExternalStorageDirectory(), p);
            if (b.exists()) return b;
            return a;
        } catch (Throwable t) { return null; }
    }

    private void onStopClicked() {
        shouldStop = true; setGeneratingState(false);
        try { if (aiClient != null) aiClient.cancel(); } catch (Throwable t) {}
        finishReasoning(); finalizeContentMarkdown();
        if (toolPhaseActive) {
            discardToolResults = true;
            for (int i = 0; i < pendingToolIds.length(); i++) {
                String tid = pendingToolIds.optString(i);
                if (tid == null || tid.length() == 0) continue;
                try {
                    JSONObject tm = new JSONObject();
                    tm.put("role", "tool");
                    tm.put("tool_call_id", tid);
                    tm.put("content", "(该工具调用在完成前被用户中断)");
                    messages.put(tm);
                } catch (Throwable t) {}
            }
        } else {
            appendPartialAssistant();
        }
        clearDraft();
        resetRound(); addStoppedRow(t("stopped_note", "已停止生成")); saveCurrentSession();
    }

    private void continueGeneration() {
        if (isGenerating) { toast("generating_short", "正在生成中"); return; }
        removeStoppedRow();
        if (roundContent.length() > 0 || roundReasoning.length() > 0) appendPartialAssistant();
        try {
            JSONObject nudge = new JSONObject();
            nudge.put("role", "user");
            nudge.put("content", "请从中断处继续完成上面的回答，不要重复已经输出的内容。");
            messages.put(nudge);
        } catch (Throwable t) {}
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);
        resetRound();
        createAiRound();
        shouldStop = false;
        discardToolResults = false;
        clearDraft();
        setGeneratingState(true);
        runAgentStep(0);
    }

    private void startAgent() {
        if (isGenerating || messages == null || messages.length() == 0) return;
        fixDanglingToolCalls();
        shouldStop = false;
        discardToolResults = false;
        clearDraft();
        setGeneratingState(true);
        runAgentStep(0);
    }

    private void regenerate() {
        if (isGenerating) { toast("generating_short", "正在生成中"); return; }
        removeStoppedRow();
        boolean removed = true;
        while (removed && messages.length() > 1) {
            removed = false; JSONObject last = messages.optJSONObject(messages.length() - 1); if (last == null) break;
            String role = UiUtils.optStr(last, "role");
            if ("assistant".equals(role) || "tool".equals(role)) { messages.remove(messages.length() - 1); removed = true; }
        }
        resetRound(); renderMessages(); if (messages.length() <= 1) return; startAgent();
    }

    private void setGeneratingState(boolean generating) {
        isGenerating = generating; if (btnSend == null) return;
        if (generating) {
            btnSend.setText(t("main_stop", "停止"));
            btnSend.setBackgroundResource(R.drawable.send_btn_stop_bg);
            btnSend.setTextColor(UiOverrides.onPrimary(this));
        } else {
            btnSend.setText(t("main_send", "发送"));
            btnSend.setBackgroundDrawable(UiOverrides.sendBtnBgDrawable(this));
            btnSend.setTextColor(UiOverrides.sendBtnText(this));
        }
    }

    private void addStoppedRow(String note) {
        if (stoppedRow != null || llMessages == null) return;
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiUtils.dp(this, 10), 0, UiUtils.dp(this, 10)); row.setLayoutParams(lp);
        TextView tv = new TextView(this); tv.setText(note == null ? t("stopped_note", "已停止生成") : note);
        tv.setTextSize(12); tv.setTextColor(UiOverrides.outline(this)); row.addView(tv);

        TextView btnGo = new TextView(this); btnGo.setText(t("stopped_continue", "继续生成"));
        btnGo.setTextSize(12); btnGo.setTextColor(UiOverrides.primary(this));
        btnGo.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 6), UiUtils.dp(this, 12), UiUtils.dp(this, 6));
        btnGo.setBackgroundResource(R.drawable.outline_btn_bg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.setMargins(UiUtils.dp(this, 10), 0, 0, 0); btnGo.setLayoutParams(glp);
        btnGo.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (guardClick()) continueGeneration(); } });
        row.addView(btnGo);

        TextView btn = new TextView(this); btn.setText(t("stopped_regen", "重新生成"));
        btn.setTextSize(12); btn.setTextColor(UiOverrides.onSurface(this));
        btn.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 6), UiUtils.dp(this, 12), UiUtils.dp(this, 6));
        btn.setBackgroundResource(R.drawable.outline_btn_bg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(UiUtils.dp(this, 8), 0, 0, 0); btn.setLayoutParams(blp);
        btn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (guardClick()) regenerate(); } });
        row.addView(btn);

        llMessages.addView(row); stoppedRow = row; scrollToBottom();
    }

    private void removeStoppedRow() { try { if (stoppedRow != null && llMessages != null) llMessages.removeView(stoppedRow); } catch (Throwable t) {} stoppedRow = null; }

    // ============================================================
    // Agent 循环
    // ============================================================

    private void runAgentStep(final int round) {
        if (shouldStop) return;
        int maxRounds = maxToolRounds();
        if (maxRounds > 0 && round >= maxRounds) {
            addSystemNote("已达到设置的最大工具调用轮数（" + maxRounds + " 轮），任务暂停。");
            setGeneratingState(false); clearDraft(); saveCurrentSession(); return;
        }
        ensureSystemMessage();

        if (round == 0 && autoCompress() && !compressing && countTurns() >= compressRounds()) {
            compressContextAndContinue();
            return;
        }

        JSONArray currentTools = tools;
        if (maxRounds > 0 && round >= maxRounds - 3) {
            currentTools = null;
            try {
                JSONObject last = messages.length() > 0 ? messages.optJSONObject(messages.length() - 1) : null;
                boolean alreadyNudged = last != null && "user".equals(UiUtils.optStr(last, "role"))
                        && UiUtils.optStr(last, "content").startsWith("【系统指令】");
                if (!alreadyNudged) {
                    JSONObject nudge = new JSONObject(); nudge.put("role", "user");
                    nudge.put("content", "【系统指令】请立即停止所有工具调用，根据已有信息给出最终回答。");
                    messages.put(nudge);
                }
            } catch (Throwable t) {}
        }

        final int fRound = round; final JSONArray fTools = currentTools;
        createAiRound(); toolPhaseActive = false;
        try {
            aiClient.chatStream(buildApiMessages(), fTools, retryTimes(), thinkingEffort(), new AiClient.StreamCallback() {
                @Override public void onReasoning(final String reasoning) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive || shouldStop || reasoning == null) return;
                        if (!isReasoningActive) { isReasoningActive = true; reasoningStartTime = System.currentTimeMillis(); }
                        roundReasoning.append(reasoning);
                        if (currentReasoningContent == null) createLiveReasoningPanel();
                        if (currentReasoningContent != null) {
                            int shown = currentReasoningContent.getText().length();
                            int len = roundReasoning.length();
                            if (shown == 0 || len - shown >= STREAM_FLUSH_STEP) currentReasoningContent.setText(roundReasoning.toString());
                        }
                        saveDraftThrottled();
                    }});
                }
                @Override public void onContent(final String content) { ui.post(new Runnable() { @Override public void run() { if (alive && !shouldStop && content != null) appendContentChunk(content); } }); }
                @Override public void onToolCall(final JSONArray toolCalls) { ui.post(new Runnable() { @Override public void run() { if (alive && !shouldStop) onRoundToolCalls(toolCalls, fRound); } }); }
                @Override public void onRetry(final int attempt) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive) return;
                        prepareRetry();
                        addSystemNote("网络异常，正在第 " + attempt + " 次重试…");
                    }});
                }
                @Override public void onTokenStats(final String stats) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive) return;
                        try {
                            if (stats.contains("输入: ")) {
                                int pStart = stats.indexOf("输入: ") + 3;
                                int pEnd = stats.indexOf(" |", pStart);
                                currentPromptTokens += Integer.parseInt(stats.substring(pStart, pEnd).trim());
                            }
                            if (stats.contains("输出: ")) {
                                int cStart = stats.indexOf("输出: ") + 3;
                                int cEnd = stats.indexOf(" |", cStart);
                                currentCompletionTokens += Integer.parseInt(stats.substring(cStart, cEnd).trim());
                            }
                            if (stats.contains("缓存命中: ")) {
                                int hStart = stats.indexOf("缓存命中: ") + 5;
                                int hEnd = stats.indexOf("%", hStart);
                                float rate = Float.parseFloat(stats.substring(hStart, hEnd).trim());
                                currentCacheHitTokens += (int)(currentPromptTokens * (rate / 100f));
                            }
                            updateTokenStatsUI();
                        } catch (Throwable t) { if (tvTokenStats != null) tvTokenStats.setText(stats); }
                    }});
                }
                @Override public void onError(final String error) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive) return; shouldStop = true; setGeneratingState(false); finishReasoning(); finalizeContentMarkdown();
                        appendPartialAssistant(); clearDraft(); resetRound(); addSystemNote("[错误] " + error); addStoppedRow(t("stopped_error", "出错了"));
                        saveCurrentSession(); notifyIfBackground("生成失败", error);
                    }});
                }
                @Override public void onComplete() {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive || shouldStop) return;
                        finishReasoning();
                        finalizeContentMarkdown();
                        boolean added = appendFinalAssistant();
                        clearDraft();
                        resetRound();
                        setGeneratingState(false);
                        saveCurrentSession();
                        if (added) {
                            try {
                                int li = messages.length() - 1;
                                JSONObject lm = messages.optJSONObject(li);
                                if (currentAiContainer != null && lm != null && "assistant".equals(UiUtils.optStr(lm, "role"))) {
                                    addAiActionRow(currentAiContainer, li);
                                }
                            } catch (Throwable t) {}
                        } else {
                            addSystemNote("本次 AI 未返回任何内容（可能触发了模型的内容安全策略，或网络返回了空响应）。可尝试换个问法重试。");
                        }
                        scrollToBottom();
                        notifyIfBackground("AI 生成完成", lastAssistantPreview());
                        if (added) speakLastAssistantIfEnabled();
                    }});
                }
            });
        } catch (Throwable t) { setGeneratingState(false); addSystemNote("[错误] " + t.getMessage()); }
    }

    private void speakLastAssistantIfEnabled() {
        try {
            if (!TtsHelper.isEnabled(this)) return;
            if (!UiUtils.getBool(this, "tts_auto_read", false)) return;
            String text = null;
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                if ("assistant".equals(UiUtils.optStr(m, "role"))) {
                    text = UiUtils.contentToText(m, true);
                    break;
                }
            }
            if (text == null || text.trim().length() == 0) return;
            TtsHelper.speak(this, text, null);
        } catch (Throwable t) {}
    }

    private void prepareRetry() {
        try {
            resetRound();
            createAiRound();
        } catch (Throwable t) {}
    }

    private String lastAssistantPreview() {
        try {
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                if ("assistant".equals(UiUtils.optStr(m, "role"))) {
                    String c = UiUtils.contentToText(m, true).replace('\n', ' ').trim();
                    if (c.length() > 40) c = c.substring(0, 40) + "…";
                    if (c.length() > 0) return c;
                }
            }
        } catch (Throwable t) {}
        return "生成完成";
    }

    private void notifyIfBackground(String title, String text) {
        try {
            if (!notifyDoneEnabled()) return;
            if (inForeground) return;
            Notifier.notifyDone(this, title, text);
        } catch (Throwable t) {}
    }

    // ============================================================
    // 流式渲染
    // ============================================================

    private void createAiRound() {
        currentAiContainer = newAiBox(-1); currentReasoningHeader = null; currentReasoningContent = null; currentContentTv = null;
        roundReasoning = new StringBuilder(); roundContent = new StringBuilder(); isReasoningActive = false; reasoningStartTime = 0L;
        lastRenderedLen = 0;
    }

    private void resetRound() {
        currentReasoningHeader = null; currentReasoningContent = null; currentContentTv = null;
        roundReasoning = new StringBuilder(); roundContent = new StringBuilder(); isReasoningActive = false; reasoningStartTime = 0L; toolPhaseActive = false;
        lastRenderedLen = 0;
    }

    private void createLiveReasoningPanel() {
        if (currentAiContainer == null) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundDrawable(UiOverrides.codeBgDrawable(this));
        panel.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 6)); panel.setLayoutParams(lp);
        currentReasoningHeader = new TextView(this); currentReasoningHeader.setText(t("reasoning_thinking", "正在深度思考..."));
        currentReasoningHeader.setTextSize(12); currentReasoningHeader.setTextColor(UiOverrides.outline(this));
        currentReasoningContent = new TextView(this); currentReasoningContent.setTextSize(13); currentReasoningContent.setLineSpacing(UiUtils.dp(this, 3), 1f);
        currentReasoningContent.setTextColor(UiOverrides.bubbleAiText(this)); currentReasoningContent.setTextIsSelectable(true);
        currentReasoningContent.setPadding(0, UiUtils.dp(this, 6), 0, 0); currentReasoningContent.setVisibility(View.GONE);
        final TextView c = currentReasoningContent;
        currentReasoningHeader.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (c != null) c.setVisibility(c.getVisibility() == View.GONE ? View.VISIBLE : View.GONE); } });
        panel.addView(currentReasoningHeader); panel.addView(currentReasoningContent); currentAiContainer.addView(panel);
    }

    private void finishReasoning() {
        if (!isReasoningActive) return; isReasoningActive = false;
        if (currentReasoningHeader != null) {
            long d = (System.currentTimeMillis() - reasoningStartTime) / 1000;
            if (d < 1) d = 1;
            currentReasoningHeader.setText(t("reasoning_done_sec", "已深度思考（%d 秒）").replace("%d", String.valueOf(d)));
        }
        if (currentReasoningContent != null) currentReasoningContent.setText(roundReasoning.toString());
    }

    private void appendContentChunk(String chunk) {
        if (chunk == null || chunk.length() == 0) return;
        finishReasoning(); roundContent.append(chunk);
        if (currentContentTv == null) {
            if (currentAiContainer == null) return;
            currentContentTv = new TextView(this); currentContentTv.setTextSize(15); currentContentTv.setLineSpacing(UiUtils.dp(this, 4), 1f);
            currentContentTv.setPadding(UiUtils.dp(this, 2), UiUtils.dp(this, 6), UiUtils.dp(this, 2), UiUtils.dp(this, 6));
            currentContentTv.setTextColor(UiOverrides.bubbleAiText(this)); currentContentTv.setTextIsSelectable(true);
            currentAiContainer.addView(currentContentTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            lastRenderedLen = 0;
        }
        int len = roundContent.length();
        if (lastRenderedLen == 0 || len - lastRenderedLen >= STREAM_FLUSH_STEP) {
            lastRenderedLen = len;
            currentContentTv.setText(roundContent.toString());
            scrollToBottom();
        }
    }

    private void finalizeContentMarkdown() {
        TextView old = currentContentTv; currentContentTv = null; lastRenderedLen = 0;
        if (old == null || currentAiContainer == null) return;
        String raw = roundContent.toString(); int index = currentAiContainer.indexOfChild(old); currentAiContainer.removeView(old);
        if (raw.trim().length() == 0) return;
        try {
            View md = MarkdownView.render(this, raw, 15f);
            if (index >= 0) currentAiContainer.addView(md, index); else currentAiContainer.addView(md);
        } catch (Throwable t) {
            TextView tv = new TextView(this); tv.setText(raw); tv.setTextSize(15); tv.setTextColor(UiOverrides.bubbleAiText(this)); tv.setTextIsSelectable(true);
            if (index >= 0) currentAiContainer.addView(tv, index); else currentAiContainer.addView(tv);
        }
        scrollToBottom();
    }

    private boolean appendFinalAssistant() {
        String content = roundContent.toString();
        String reasoning = roundReasoning.toString();
        if (content.trim().length() == 0 && reasoning.trim().length() == 0) return false;
        try {
            JSONObject m = new JSONObject();
            m.put("role", "assistant");
            m.put("content", content);
            if (reasoning.length() > 0) m.put("reasoning_content", reasoning);
            messages.put(m);
            return true;
        } catch (Throwable t) { return false; }
    }

    private void appendPartialAssistant() {
        if (roundContent.length() == 0 && roundReasoning.length() == 0) return;
        appendFinalAssistant();
    }

    // ============================================================
    // 工具调用
    // ============================================================

    private void onRoundToolCalls(final JSONArray toolCalls, final int round) {
        if (toolCalls == null) return;
        finishReasoning(); finalizeContentMarkdown();
        try {
            JSONObject am = new JSONObject(); am.put("role", "assistant"); am.put("tool_calls", toolCalls);
            if (roundContent.length() > 0) am.put("content", roundContent.toString());
            if (roundReasoning.length() > 0) am.put("reasoning_content", roundReasoning.toString());
            messages.put(am);
        } catch (Throwable t) {}
        resetRound(); executeTools(toolCalls, round);
    }

    private String runTool(String tool, String args) {
        try {
            if (tool == null) return "未知工具";
            if (tool.startsWith("plugin_")) return PluginManager.execute(this, tool, args);
            if ("run_shell_command".equals(tool)) return ShellToolExecutor.execute(this, args);
            if ("run_js".equals(tool)) return JsToolExecutor.execute(this, args);
            if ("accessibility_control".equals(tool)) return AccessibilityController.execute(this, args);
            if ("web_search".equals(tool) || "fetch_url".equals(tool)) return webExecutor.execute(tool, args);
if ("generate_image".equals(tool)) return ImageGenClient.generateSync(this, args);
if ("ask_user".equals(tool)) return "(ask_user 已被拦截)";
return fileExecutor.execute(tool, args);
        } catch (Throwable t) {
            return "工具执行失败: " + t.getMessage();
        }
    }

    private void executeTools(final JSONArray toolCalls, final int round) {
        toolPhaseActive = true;
        discardToolResults = false;
        final int myBatch = ++toolBatchId;
        pendingToolIds = new JSONArray();
        final List<ToolOutcome> outs = new ArrayList<ToolOutcome>();
        JSONArray normalCalls = new JSONArray();
        JSONObject askCall = null;
        JSONObject deleteCall = null;

        String repeatBlock = buildRepeatBlock(toolCalls);

        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject tc = toolCalls.optJSONObject(i);
            if (tc == null) continue;
            String id = UiUtils.optStr(tc, "id");
            JSONObject fn = tc.optJSONObject("function");
            String name = fn == null ? "" : UiUtils.optStr(fn, "name");
            if (id.length() > 0) pendingToolIds.put(id);

            if (repeatBlock.length() > 0 && "read_file".equals(name)) {
                try {
                    JSONObject tm = new JSONObject();
                    tm.put("role", "tool");
                    tm.put("tool_call_id", id);
                    tm.put("content", repeatBlock);
                    messages.put(tm);
                    addToolPanel(currentAiContainer, name, repeatBlock, false);
                } catch (Throwable t) {}
                continue;
            }

            if ("ask_user".equals(name) && askCall == null) {
                askCall = tc;
            } else if ("delete_file".equals(name) && deleteCall == null) {
                deleteCall = tc;
            } else {
                normalCalls.put(tc);
            }
        }

        if (askCall != null) {
            final JSONObject tc = askCall;
            JSONObject fn = tc.optJSONObject("function");
            String args = fn == null ? "{}" : UiUtils.optStr(fn, "arguments");
            toolPhaseActive = false;
            askUser(tc, args, normalCalls, round);
            return;
        }

        if (deleteCall != null) {
            final JSONObject tc = deleteCall;
            JSONObject fn = tc.optJSONObject("function");
            String args = fn == null ? "{}" : UiUtils.optStr(fn, "arguments");
            toolPhaseActive = false;
            confirmThenDelete(tc, args, normalCalls, round);
            return;
        }

        for (int i = 0; i < normalCalls.length(); i++) {
            JSONObject tc = normalCalls.optJSONObject(i);
            if (tc == null) continue;
            ToolOutcome o = new ToolOutcome();
            o.id = UiUtils.optStr(tc, "id");
            JSONObject fn = tc.optJSONObject("function");
            o.name = fn == null ? "unknown" : UiUtils.optStr(fn, "name");
            o.args = fn == null ? "{}" : UiUtils.optStr(fn, "arguments");
            if (o.args == null || o.args.length() == 0) o.args = "{}";
            outs.add(o);
        }
        if (outs.isEmpty()) {
            toolPhaseActive = false;
            runAgentStep(round + 1);
            return;
        }

        new Thread(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < outs.size(); i++) {
                    ToolOutcome o = outs.get(i);
                    try {
                        o.result = runTool(o.name, o.args);
                        if (o.result != null && o.result.length() > 500000) {
                            o.result = o.result.substring(0, 500000) + "\n...(结果过长已截断)";
                        }
                    } catch (Throwable t) { o.result = "工具执行失败: " + t.getMessage(); }
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (myBatch != toolBatchId) return;
                        toolPhaseActive = false;
                        if (!alive) {
                            for (int i = 0; i < outs.size(); i++) {
                                ToolOutcome o = outs.get(i);
                                try { JSONObject tm = new JSONObject(); tm.put("role", "tool"); tm.put("tool_call_id", o.id); tm.put("content", aiFacingText(o.result)); messages.put(tm); } catch (Throwable t) {}
                            }
                            if (!shouldStop) runAgentStep(round + 1);
                            return;
                        }
                        if (discardToolResults) { discardToolResults = false; scrollToBottom(); return; }
                        for (int i = 0; i < outs.size(); i++) {
                            ToolOutcome o = outs.get(i);
                            try {
                                if (o.result != null && o.result.startsWith("@@SHOT@@")) {
                                    String b64 = o.result.substring(8);
                                    String shotMeta = "";
                                    try {
                                        int vw = ProjectionController.visionImageWidth(), vh = ProjectionController.visionImageHeight();
                                        if (vw > 0 && vh > 0) shotMeta = "截图像素尺寸 " + vw + "×" + vh + "（图上叠加了 10% 网格线与边缘像素刻度）。";
                                    } catch (Throwable t) {}
                                    JSONObject tm = new JSONObject();
                                    tm.put("role", "tool");
                                    tm.put("tool_call_id", o.id);
                                    tm.put("content", "已截取当前屏幕，截图自动附在下一条消息里。" + shotMeta
                                            + "请看图分析并返回下一步操作（tap/long_press/swipe 时传 from_vision=true，坐标直接用截图上的像素坐标，App 会按比例换算成屏幕坐标）。");
                                    messages.put(tm);
                                    addToolPanel(currentAiContainer, o.name, "(截屏成功，图片已附到对话)", false);
                                    appendVisionShot(b64);
                                    continue;
                                }
                                if (o.result != null && (o.result.startsWith(WebToolExecutor.SEARCH_PREFIX)
                                        || o.result.startsWith(WebToolExecutor.READER_PREFIX))) {
                                    String aiText = aiFacingText(o.result);
                                    JSONObject tm = new JSONObject();
                                    tm.put("role", "tool");
                                    tm.put("tool_call_id", o.id);
                                    tm.put("content", aiText);
                                    messages.put(tm);
                                    addSearchOrReaderCard(currentAiContainer, o.result);
                                    continue;
                                }
                                if (o.result != null && o.result.startsWith(ImageGenClient.IMAGE_PREFIX)) {
    String b64 = o.result.substring(ImageGenClient.IMAGE_PREFIX.length());
    JSONObject tm = new JSONObject();
    tm.put("role", "tool");
    tm.put("tool_call_id", o.id);
    tm.put("content", "已生成图片，图片自动附在下一条消息里，请查看后继续对话。");
    messages.put(tm);
    addToolPanel(currentAiContainer, o.name, "(图片已生成并附到对话)", false);
    appendGeneratedImage(b64);
    continue;
}
                                JSONObject tm = new JSONObject();
                                tm.put("role", "tool");
                                tm.put("tool_call_id", o.id);
                                tm.put("content", o.result);
                                messages.put(tm);
                                addToolPanel(currentAiContainer, o.name, o.result, false);
                            } catch (Throwable t) {}
                        }
                        scrollToBottom();
                        if (shouldStop) { setGeneratingState(false); addStoppedRow(t("stopped_note", "已停止生成")); saveCurrentSession(); return; }
                        runAgentStep(round + 1);
                    }
                });
            }
        }).start();
    }

    private String aiFacingText(String raw) {
        if (raw == null) return "";
        if (raw.startsWith(WebToolExecutor.SEARCH_PREFIX)) return WebToolExecutor.searchResultToAiText(raw);
        if (raw.startsWith(WebToolExecutor.READER_PREFIX)) return WebToolExecutor.readerResultToAiText(raw);
        return raw;
    }

    private void addSearchOrReaderCard(LinearLayout container, String raw) {
        if (container == null || raw == null) return;
        try {
            if (raw.startsWith(WebToolExecutor.SEARCH_PREFIX)) {
                JSONObject o = new JSONObject(raw.substring(WebToolExecutor.SEARCH_PREFIX.length()));
                addSearchResultCard(container, o);
            } else if (raw.startsWith(WebToolExecutor.READER_PREFIX)) {
                JSONObject o = new JSONObject(raw.substring(WebToolExecutor.READER_PREFIX.length()));
                addReaderCard(container, o);
            }
        } catch (Throwable t) {}
    }

    private void addSearchResultCard(LinearLayout container, JSONObject o) {
        try {
            String query = o.optString("query", "");
            String provider = o.optString("provider", "");
            JSONArray items = o.optJSONArray("items");
            if (items == null || items.length() == 0) {
                addToolPanel(container, "web_search", "（无搜索结果）", false);
                return;
            }

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundDrawable(UiOverrides.codeBgDrawable(this));
            panel.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 6));
            panel.setLayoutParams(lp);

            final TextView header = new TextView(this);
            StringBuilder hb = new StringBuilder();
            hb.append(t("card_search_results", "搜索结果"));
            if (query.length() > 0) hb.append("：").append(query);
            hb.append("（").append(items.length()).append("）");
            if (provider.length() > 0) hb.append("  ").append(provider);
            header.setText(hb.toString());
            header.setTextSize(12);
            header.setTextColor(UiOverrides.outline(this));
            header.setPadding(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 2));
            panel.addView(header);

            final LinearLayout contentBox = new LinearLayout(this);
            contentBox.setOrientation(LinearLayout.VERTICAL);
            contentBox.setVisibility(View.GONE);

            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                final String title = it.optString("title", "");
                final String link = it.optString("link", "");
                String media = it.optString("media", "");
                String content = it.optString("content", "");
                String pd = it.optString("publish_date", "");

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 8), UiUtils.dp(this, 4), UiUtils.dp(this, 8));

                TextView tvTitle = new TextView(this);
                tvTitle.setText((i + 1) + ". " + (title.length() == 0 ? link : title));
                tvTitle.setTextSize(13);
                tvTitle.setTextColor(UiOverrides.primary(this));
                tvTitle.setSingleLine(false);
                item.addView(tvTitle);

                if (media.length() > 0 || pd.length() > 0) {
                    TextView tvMeta = new TextView(this);
                    StringBuilder sb = new StringBuilder();
                    if (media.length() > 0) sb.append(media);
                    if (pd.length() > 0) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(pd);
                    }
                    tvMeta.setText(sb.toString());
                    tvMeta.setTextSize(11);
                    tvMeta.setTextColor(UiOverrides.outline(this));
                    item.addView(tvMeta);
                }

                if (content.length() > 0) {
                    if (content.length() > 200) content = content.substring(0, 200) + "…";
                    TextView tvContent = new TextView(this);
                    tvContent.setText(content);
                    tvContent.setTextSize(12);
                    tvContent.setTextColor(UiOverrides.bubbleAiText(this));
                    tvContent.setMaxLines(4);
                    tvContent.setPadding(0, UiUtils.dp(this, 2), 0, 0);
                    item.addView(tvContent);
                }

                if (link.length() > 0) {
                    final String fLink = link;
                    final String fTitle = title.length() == 0 ? link : title;
                    item.setClickable(true);
                    item.setFocusable(true);
                    item.setBackgroundResource(R.drawable.session_item_bg);
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { openInWebView(fLink, fTitle); }
                    });
                }
                contentBox.addView(item);

                if (i < items.length() - 1) {
                    View div = new View(this);
                    div.setBackgroundColor(UiOverrides.divider(this));
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 1));
                    dlp.setMargins(0, UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4));
                    contentBox.addView(div, dlp);
                }
            }
            panel.addView(contentBox);

            header.setClickable(true);
            header.setFocusable(true);
            header.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean show = contentBox.getVisibility() == View.GONE;
                    contentBox.setVisibility(show ? View.VISIBLE : View.GONE);
                    String t2 = header.getText().toString();
                    if (show) { if (!t2.contains("点击收起")) header.setText(t2 + "  " + t("tool_panel_collapse", "（点击收起）")); }
                    else header.setText(t2.replace("  " + t("tool_panel_collapse", "（点击收起）"), ""));
                }
            });

            container.addView(panel);
        } catch (Throwable t) {
            addToolPanel(container, "web_search", "（渲染搜索结果卡片失败）", false);
        }
    }

    private void addReaderCard(LinearLayout container, JSONObject o) {
        try {
            final String title = o.optString("title", "");
            final String url = o.optString("url", "");
            String content = o.optString("content", "");
            String desc = o.optString("description", "");

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundDrawable(UiOverrides.codeBgDrawable(this));
            panel.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 6));
            panel.setLayoutParams(lp);

            final TextView header = new TextView(this);
            header.setText(t("card_reader", "网页阅读：") + (title.length() == 0 ? url : title));
            header.setTextSize(12);
            header.setTextColor(UiOverrides.outline(this));
            header.setPadding(0, UiUtils.dp(this, 2), 0, UiUtils.dp(this, 2));
            panel.addView(header);

            final LinearLayout contentBox = new LinearLayout(this);
            contentBox.setOrientation(LinearLayout.VERTICAL);
            contentBox.setVisibility(View.GONE);

            if (url.length() > 0) {
                TextView tvUrl = new TextView(this);
                tvUrl.setText(url);
                tvUrl.setTextSize(11);
                tvUrl.setTextColor(UiOverrides.outline(this));
                tvUrl.setSingleLine(true);
                tvUrl.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                contentBox.addView(tvUrl);
            }

            if (desc.length() > 0) {
                TextView tvDesc = new TextView(this);
                tvDesc.setText(desc);
                tvDesc.setTextSize(12);
                tvDesc.setTextColor(UiOverrides.bubbleAiText(this));
                tvDesc.setPadding(0, UiUtils.dp(this, 4), 0, 0);
                contentBox.addView(tvDesc);
            }

            if (content.length() > 0) {
                String preview = content.length() > 2000 ? content.substring(0, 2000) + "…" : content;
                TextView tvContent = new TextView(this);
                tvContent.setText(preview);
                tvContent.setTextSize(12);
                tvContent.setTextColor(UiOverrides.bubbleAiText(this));
                tvContent.setPadding(0, UiUtils.dp(this, 6), 0, 0);
                contentBox.addView(tvContent);
            }

            if (url.length() > 0) {
                TextView btnOpen = new TextView(this);
                btnOpen.setText(t("card_open_browser", "在浏览器中打开 ›"));
                btnOpen.setTextSize(12);
                btnOpen.setTextColor(UiOverrides.primary(this));
                btnOpen.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 8), UiUtils.dp(this, 4), UiUtils.dp(this, 4));
                btnOpen.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openInWebView(url, title); }
                });
                contentBox.addView(btnOpen);
            }
            panel.addView(contentBox);

            header.setClickable(true);
            header.setFocusable(true);
            header.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean show = contentBox.getVisibility() == View.GONE;
                    contentBox.setVisibility(show ? View.VISIBLE : View.GONE);
                    String t2 = header.getText().toString();
                    if (show) { if (!t2.contains("点击收起")) header.setText(t2 + "  " + t("tool_panel_collapse", "（点击收起）")); }
                    else header.setText(t2.replace("  " + t("tool_panel_collapse", "（点击收起）"), ""));
                }
            });

            container.addView(panel);
        } catch (Throwable t) {}
    }

    private void openInWebView(String url, String title) {
        try {
            Intent it = new Intent(MainActivity.this, WebViewActivity.class);
            it.putExtra(WebViewActivity.EXTRA_URL, url);
            it.putExtra(WebViewActivity.EXTRA_TITLE, title);
            startActivity(it);
        } catch (Throwable t) {
            Toast.makeText(this, t("err_no_web", "无法打开浏览器: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // ask_user / delete_file
    // ============================================================

    private void askUser(final JSONObject askCall, String argsJson,
                         final JSONArray otherCalls, final int round) {
        final String question;
        final String optionsStr;
        try {
            JSONObject a = new JSONObject(argsJson == null ? "{}" : argsJson);
            question = a.optString("question", "（AI 想问你一件事）");
            optionsStr = a.optString("options", "");
        } catch (Throwable t) {
            question = "（AI 想问你一件事）";
            optionsStr = "";
        }

        final EditText et = new EditText(this);
        et.setTextSize(15);
        et.setMinLines(2);
        et.setMaxLines(6);
        et.setHint(t("ask_dialog_hint", "在此输入你的回答…"));
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        final String[] presets;
        if (optionsStr != null && optionsStr.trim().length() > 0) {
            String[] parts = optionsStr.split(",");
            List<String> lst = new ArrayList<String>();
            for (int i = 0; i < parts.length; i++) {
                String s = parts[i].trim();
                if (s.length() > 0) lst.add(s);
            }
            presets = lst.toArray(new String[lst.size()]);
        } else {
            presets = new String[0];
        }

        if (presets.length > 0) {
            String[] items = new String[presets.length + 1];
            System.arraycopy(presets, 0, items, 0, presets.length);
            items[presets.length] = t("ask_self_input", "自己输入");
            final String fQuestion = question;
            new GlassDialog.Builder(this)
                    .setTitle(t("ask_dialog_title", "AI 想问你"))
                    .setMessage(fQuestion)
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            d.dismiss();
                            if (which < presets.length) et.setText(presets[which]);
                            showAskInputDialog(fQuestion, et, askCall, otherCalls, round);
                        }
                    })
                    .setNegativeButton(t("common_cancel", "取消"), null)
                    .show();
        } else {
            showAskInputDialog(question, et, askCall, otherCalls, round);
        }
    }

    private void showAskInputDialog(String question, final EditText et,
                                    final JSONObject askCall, final JSONArray otherCalls, final int round) {
        new GlassDialog.Builder(this)
                .setTitle(t("ask_dialog_title", "AI 想问你"))
                .setMessage(question)
                .setView(et)
                .setPositiveButton(t("ask_answer", "回答"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String answer = et.getText().toString().trim();
                        if (answer.length() == 0) answer = "(用户没有填写内容)";
                        d.dismiss();
                        answerToAsk(askCall, answer, otherCalls, round);
                    }
                })
                .setNegativeButton(t("ask_cancel", "取消回答"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        answerToAsk(askCall, "(用户取消了回答，请基于已有信息继续)", otherCalls, round);
                    }
                })
                .show();
    }

    private void answerToAsk(JSONObject askCall, String answer,
                             JSONArray otherCalls, int round) {
        try {
            String id = UiUtils.optStr(askCall, "id");
            JSONObject tm = new JSONObject();
            tm.put("role", "tool");
            tm.put("tool_call_id", id);
            tm.put("content", "用户的回答: " + answer);
            messages.put(tm);
            addToolPanel(currentAiContainer, "ask_user", "用户回答: " + answer, false);
        } catch (Throwable t) {}
        if (otherCalls != null && otherCalls.length() > 0) {
            executeTools(otherCalls, round);
        } else {
            runAgentStep(round + 1);
        }
    }

    private void confirmThenDelete(final JSONObject deleteCall, String argsJson,
                                   final JSONArray otherCalls, final int round) {
        String path = "";
        boolean recursive = false;
        try {
            JSONObject a = new JSONObject(argsJson == null ? "{}" : argsJson);
            path = a.optString("path", "");
            recursive = a.optBoolean("recursive", false);
        } catch (Throwable t) {}

        int sec = UiUtils.getInt(this, "delete_confirm_sec", 5);
        if (sec <= 0) {
            doDelete(deleteCall, argsJson, otherCalls, round);
            return;
        }

        final String fpath = path;
        final boolean frec = recursive;
        final String[] finalArgs = new String[]{argsJson};
        final android.app.Dialog[] dlgHolder = new android.app.Dialog[1];

        String initMsg = "AI 想删除:" + (char)10 + fpath + (frec ? "（含目录内容）" : "")
                + (char)10 + (char)10 + sec + " 秒后自动同意…";

        final android.os.CountDownTimer timer = new android.os.CountDownTimer((long) sec * 1000, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                try {
                    if (dlgHolder[0] != null) {
                        String m = "AI 想删除:" + (char)10 + fpath + (frec ? "（含目录内容）" : "")
                                + (char)10 + (char)10 + (millisUntilFinished / 1000) + " 秒后自动同意…";
                        GlassDialog.updateMessage(dlgHolder[0], m);
                    }
                } catch (Throwable t) {}
            }
            @Override public void onFinish() {
                try { if (dlgHolder[0] != null) dlgHolder[0].dismiss(); } catch (Throwable t) {}
                doDelete(deleteCall, finalArgs[0], otherCalls, round);
            }
        };

        dlgHolder[0] = new GlassDialog.Builder(this)
                .setTitle(t("delete_confirm_title", "确认删除"))
                .setMessage(initMsg)
                .setPositiveButton(t("delete_confirm_now", "立即删除"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try { timer.cancel(); } catch (Throwable t) {}
                        d.dismiss();
                        doDelete(deleteCall, finalArgs[0], otherCalls, round);
                    }
                })
                .setNegativeButton(t("delete_confirm_reject", "拒绝"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try { timer.cancel(); } catch (Throwable t) {}
                        d.dismiss();
                        rejectDelete(deleteCall, otherCalls, round);
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface d) {
                        try { timer.cancel(); } catch (Throwable t) {}
                    }
                })
                .show();

        try { timer.start(); } catch (Throwable t) {}
    }

    private void doDelete(JSONObject deleteCall, String argsJson, JSONArray otherCalls, int round) {
        try {
            String id = UiUtils.optStr(deleteCall, "id");
            JSONObject fn = deleteCall.optJSONObject("function");
            String name = fn == null ? "delete_file" : UiUtils.optStr(fn, "name");
            String result = fileExecutor.execute(name, argsJson);
            JSONObject tm = new JSONObject();
            tm.put("role", "tool");
            tm.put("tool_call_id", id);
            tm.put("content", result);
            messages.put(tm);
            addToolPanel(currentAiContainer, name, result, false);
        } catch (Throwable t) {}
        if (otherCalls != null && otherCalls.length() > 0) executeTools(otherCalls, round);
        else runAgentStep(round + 1);
    }

    private void rejectDelete(JSONObject deleteCall, JSONArray otherCalls, int round) {
        try {
            String id = UiUtils.optStr(deleteCall, "id");
            JSONObject tm = new JSONObject();
            tm.put("role", "tool");
            tm.put("tool_call_id", id);
            tm.put("content", "用户拒绝了这个删除操作，请勿再尝试删除同一路径。");
            messages.put(tm);
            addToolPanel(currentAiContainer, "delete_file", "(用户拒绝删除)", false);
        } catch (Throwable t) {}
        if (otherCalls != null && otherCalls.length() > 0) executeTools(otherCalls, round);
        else runAgentStep(round + 1);
    }

    private void fixDanglingToolCalls() {
        try {
            if (messages == null || messages.length() == 0) return;
            JSONArray out = new JSONArray();
            JSONArray pending = new JSONArray();
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("assistant".equals(role)) {
                    flushPlaceholders(out, pending); pending = new JSONArray();
                    out.put(m);
                    JSONArray tcs = m.optJSONArray("tool_calls");
                    if (tcs != null) for (int k = 0; k < tcs.length(); k++) {
                        JSONObject tc = tcs.optJSONObject(k); if (tc == null) continue;
                        String id = UiUtils.optStr(tc, "id");
                        if (id.length() > 0) pending.put(id);
                    }
                } else if ("tool".equals(role)) {
                    String id = UiUtils.optStr(m, "tool_call_id");
                    int idx = indexOfStr(pending, id);
                    if (idx >= 0) { pending.remove(idx); out.put(m); }
                } else {
                    flushPlaceholders(out, pending); pending = new JSONArray();
                    out.put(m);
                }
            }
            flushPlaceholders(out, pending);
            messages = out;
        } catch (Throwable t) {}
    }

    private void flushPlaceholders(JSONArray out, JSONArray ids) {
        for (int i = 0; i < ids.length(); i++) {
            try {
                JSONObject tm = new JSONObject();
                tm.put("role", "tool");
                tm.put("tool_call_id", ids.optString(i));
                tm.put("content", "(该工具调用未完成，已被中断)");
                out.put(tm);
            } catch (Throwable t) {}
        }
    }

    private int indexOfStr(JSONArray arr, String s) {
        if (arr == null || s == null) return -1;
        for (int i = 0; i < arr.length(); i++) if (s.equals(arr.optString(i))) return i;
        return -1;
    }

    private String buildRepeatBlock(JSONArray newCalls) {
        try {
            List<String> newPaths = new ArrayList<String>();
            for (int i = 0; i < newCalls.length(); i++) {
                JSONObject tc = newCalls.optJSONObject(i);
                if (tc == null) continue;
                JSONObject fn = tc.optJSONObject("function");
                if (fn == null) continue;
                String name = UiUtils.optStr(fn, "name");
                if (!"read_file".equals(name)) continue;
                try {
                    JSONObject a = new JSONObject(UiUtils.optStr(fn, "arguments"));
                    String p = a.optString("path", "");
                    if (p.length() > 0) newPaths.add(p);
                } catch (Throwable t) {}
            }
            if (newPaths.isEmpty()) return "";

            for (int k = 0; k < newPaths.size(); k++) {
                String path = newPaths.get(k);
                int failCount = 0;
                for (int i = messages.length() - 1; i >= 0; i--) {
                    JSONObject m = messages.optJSONObject(i);
                    if (m == null) continue;
                    String role = UiUtils.optStr(m, "role");
                    if ("tool".equals(role)) {
                        String content = UiUtils.optStr(m, "content");
                        if (content.startsWith("文件不存在") && content.contains(path)) failCount++;
                    }
                    if (failCount >= 3) break;
                }
                if (failCount >= 3) {
                    return "【系统提示】路径 " + path + " 已经连续多次读取失败（文件不存在）。"
                         + "请不要再读取这个文件，改用 list_files 查看目录，或直接告诉用户此文件不存在。";
                }
            }
        } catch (Throwable t) {}
        return "";
    }

    // ============================================================
    // 上下文压缩
    // ============================================================

    private void compressContextAndContinue() {
        compressing = true;
        setGeneratingState(true);
        addSystemNote(t("compress_note", "上下文较长，正在把早期对话压缩成摘要…"));

        final int keep = Math.max(2, compressRounds() / 2);
        final int total = messages.length();
        final int from = Math.max(1, total - keep);

        String prevSummary = ctxSummary();
        int prevFrom = ctxFrom();
        String transcript;
        if (prevSummary.length() > 0) {
            String newer = buildTranscript(Math.max(1, prevFrom), from);
            if (newer.trim().length() == 0) transcript = prevSummary;
            else transcript = prevSummary + "\n\n【以下为此后新增的对话】\n" + newer;
        } else {
            transcript = buildTranscript(1, from);
        }
        if (transcript.trim().length() == 0) { compressing = false; runAgentStep(0); return; }

        JSONArray req = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是一个对话压缩器。把用户给出的对话记录压缩成结构化要点，必须保留："
                    + "① 用户的目标、要求与偏好；② 已经确认的事实与结论；③ 涉及的文件路径、命令与关键代码片段；"
                    + "④ 尚未完成的事项。不要编造内容，不要输出多余解释，直接输出摘要。");
            req.put(sys);
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", transcript);
            req.put(u);
        } catch (Throwable t) {}

        try {
            aiClient.chatOnce(req, new AiClient.OnceCallback() {
                @Override public void onResult(final String text) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive) { compressing = false; return; }
                        compressing = false;
                        applyCompression(text, from);
                        addSystemNote(t("compress_done", "上下文已压缩：对话原文保留在本会话中，AI 之后只看到「摘要 + 最近部分」"));
                        runAgentStep(0);
                    }});
                }
                @Override public void onError(final String message) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (!alive) { compressing = false; return; }
                        compressing = false;
                        addSystemNote(t("compress_fail", "上下文压缩失败，继续使用完整上下文"));
                        runAgentStep(0);
                    }});
                }
            });
        } catch (Throwable t) {
            compressing = false;
            runAgentStep(0);
        }
    }

    private String buildTranscript(int from, int to) {
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = from; i < to && i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("user".equals(role)) sb.append("【用户】").append(UiUtils.contentToText(m, true)).append("\n");
                else if ("assistant".equals(role)) {
                    String c = UiUtils.contentToText(m, true);
                    String r = UiUtils.optStr(m, "reasoning_content");
                    if (r.length() > 0) {
                        if (r.length() > 800) r = r.substring(0, 800) + "…";
                        sb.append("【助手思考】").append(r).append("\n");
                    }
                    if (c.trim().length() > 0) sb.append("【助手】").append(c).append("\n");
                } else if ("tool".equals(role)) {
                    String c = UiUtils.optStr(m, "content");
                    if (c.length() > 600) c = c.substring(0, 600) + "…";
                    sb.append("【工具结果】").append(c).append("\n");
                }
            }
        } catch (Throwable t) {}
        return sb.toString();
    }

    private JSONArray buildApiMessages() {
        try {
            String sum = ctxSummary();
            int from = ctxFrom();
            int total = messages.length();
            int start = 1;
            if (sum.length() > 0) {
                start = -1;
                for (int i = Math.max(1, from); i < total; i++) {
                    JSONObject m = messages.optJSONObject(i);
                    if (m != null && "user".equals(UiUtils.optStr(m, "role"))) { start = i; break; }
                }
                if (start < 0) start = total;
            }

            JSONArray out = new JSONArray();
            if (sum.length() > 0) {
                JSONObject sys = cleanApiMsg(messages.optJSONObject(0));
                if (sys != null) out.put(sys);
                JSONObject u = new JSONObject();
                u.put("role", "user");
                u.put("content", "【前期对话摘要（原文已归档，以下摘要代表之前全部对话）】\n" + sum);
                out.put(u);
                JSONObject a = new JSONObject();
                a.put("role", "assistant");
                a.put("content", "好的，我已了解之前的上下文，会在此基础上继续。");
                out.put(a);
            }
            for (int i = start; i < total; i++) {
                JSONObject m = cleanApiMsg(messages.optJSONObject(i));
                if (m != null) out.put(m);
            }
            return out;
        } catch (Throwable t) {
            return messages;
        }
    }

    private JSONObject cleanApiMsg(JSONObject m) {
        if (m == null) return null;
        try {
            JSONObject o = new JSONObject(m.toString());
            o.remove("x_ctx_summary");
            o.remove("x_ctx_from");
            Object c = o.opt("content");
            if (c instanceof JSONArray) {
                JSONArray parts = (JSONArray) c;
                for (int k = 0; k < parts.length(); k++) {
                    JSONObject p = parts.optJSONObject(k);
                    if (p == null || !"image_url".equals(p.optString("type"))) continue;
                    JSONObject u = p.optJSONObject("image_url");
                    if (u == null) continue;
                    String url = u.optString("url");
                    if (url.startsWith("ref:")) {
                        String b64 = UiUtils.resolveImageBase64(url);
                        if (b64 != null) u.put("url", "data:image/jpeg;base64," + b64);
                    }
                }
                o.put("content", parts);
            }
            return o;
        } catch (Throwable t) { return m; }
    }

    private String ctxSummary() {
        try {
            JSONObject sys = messages.optJSONObject(0);
            return sys == null ? "" : UiUtils.optStr(sys, "x_ctx_summary");
        } catch (Throwable t) { return ""; }
    }

    private int ctxFrom() {
        try {
            JSONObject sys = messages.optJSONObject(0);
            return sys == null ? 1 : Math.max(1, sys.optInt("x_ctx_from", 1));
        } catch (Throwable t) { return 1; }
    }

    private void applyCompression(String summary, int from) {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null || !"system".equals(UiUtils.optStr(m, "role"))) continue;
                m.put("x_ctx_summary", summary == null ? "" : summary);
                m.put("x_ctx_from", from);
                break;
            }
            saveCurrentSession();
        } catch (Throwable t) {}
    }

    private void clearCtxSummary() {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null || !"system".equals(UiUtils.optStr(m, "role"))) continue;
                m.remove("x_ctx_summary");
                m.remove("x_ctx_from");
                break;
            }
            saveCurrentSession();
            addSystemNote(t("ctx_restored", "已恢复完整上下文（清除压缩摘要，AI 将重新看到全部对话原文）"));
        } catch (Throwable t) {}
    }

    // ============================================================
    // 更多菜单
    // ============================================================

    private void showMoreMenu() {
        List<String> items = new ArrayList<String>();
        items.add(t("more_export_md", "导出当前会话为 Markdown"));
        items.add(t("more_share_session", "分享当前会话全文"));
        items.add(t("more_copy_last", "复制最后一条回复"));
        items.add(t("more_import_md", "导入Markdown会话"));
        items.add(t("more_import_json", "导入JSON会话"));
        items.add(t("more_compress", "压缩当前上下文"));
        items.add(t("more_restore_ctx", "恢复完整上下文（清除压缩摘要）"));
        items.add(t("more_clear_session", "清空当前会话内容"));
        items.add(t("more_open_settings", "打开设置"));

        final List<JSONObject> mainPlugins;
        try {
            mainPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_MAIN_MENU);
        } catch (Throwable t) {
            mainPlugins = new ArrayList<JSONObject>();
        }
        for (int i = 0; i < mainPlugins.size(); i++) {
            JSONObject p = mainPlugins.get(i);
            items.add(p.optString("title", p.optString("name", "插件")));
        }

        final List<String> fItems = items;
        GlassMenuDialog.showItems(this, t("more_title", "更多"), fItems.toArray(new String[0]),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) exportCurrentSession();
                        else if (which == 1) shareText(messagesToMarkdown(messages, sessionTitle()));
                        else if (which == 2) copyLastAssistant();
                        else if (which == 3) importMarkdownSession();
                        else if (which == 4) importJsonSession();
                        else if (which == 5) manualCompress();
                        else if (which == 6) clearCtxSummary();
                        else if (which == 7) clearCurrentSession();
                        else if (which == 8) startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        else if (which >= 9) {
                            int idx = which - 9;
                            if (idx >= 0 && idx < mainPlugins.size()) {
                                final JSONObject p = mainPlugins.get(idx);
                                UiPluginDialog.show(MainActivity.this, p, new UiPluginDialog.OnSubmitListener() {
                                    @Override public void onSubmit(final JSONObject args) {
                                        runUiPluginInline(p, args);
                                    }
                                });
                            }
                        }
                    }
                });
    }

    private void copyLastAssistant() {
        try {
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                if ("assistant".equals(UiUtils.optStr(m, "role"))) {
                    String c = UiUtils.contentToText(m, true);
                    if (c.trim().length() > 0) { MarkdownView.copyText(this, c); return; }
                }
            }
            toast("no_reply_to_copy", "还没有可复制的回复");
        } catch (Throwable t) {}
    }

    private void manualCompress() {
        if (isGenerating) { toast("generating_short", "正在生成中"); return; }
        if (countTurns() < 4) { toast("err_too_short", "对话太短，不需要压缩"); return; }
        compressContextAndContinue();
    }

    private void clearCurrentSession() {
        new GlassDialog.Builder(this)
                .setTitle(t("chat_empty", "清空当前会话的内容？"))
                .setPositiveButton(t("chat_clear", "清空"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            messages = new JSONArray();
                            ensureSystemMessage();
                            resetRound(); renderMessages(); saveCurrentSession();
                        } catch (Throwable t) {}
                        d.dismiss();
                    }
                })
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();
    }

    // ============================================================
    // @ 引用文件
    // ============================================================

    private File pickerDir = null;

    private void showFilePicker() {
        try {
            if (pickerDir == null || !pickerDir.exists()) {
                pickerDir = Environment.getExternalStorageDirectory();
                if (pickerDir == null || !pickerDir.exists()) pickerDir = fileExecutor.getWorkspace();
            }
            showPickerDialog(pickerDir);
        } catch (Throwable t) {
            toast("file_picker_no_browser", "无法打开文件浏览器");
        }
    }

    private void showPickerDialog(final File dir) {
        final List<File> items = fileExecutor.listDir(dir);
        List<String> names = new ArrayList<String>();
        names.add(t("file_picker_up", "..  返回上一级"));
        for (int i = 0; i < items.size(); i++) {
            File f = items.get(i);
            String extra = f.isFile() ? "  (" + UiUtils.formatSize(f.length()) + ")" : "";
            names.add((f.isDirectory() ? "[目录] " : "[文件] ") + f.getName() + extra);
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names));
        wrap.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 340)));

        final android.app.Dialog dlg = new GlassDialog.Builder(this)
                .setTitle(dir.getAbsolutePath() + "\n" + t("file_picker_selected", "已选 %d 个文件").replace("%d", String.valueOf(pendingRefFiles.size())))
                .setView(wrap)
                .setPositiveButton(t("file_picker_done", "完成"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { finishRefPick(); }
                })
                .setNeutralButton(t("file_picker_clear", "清空已选"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        pendingRefFiles.clear();
                        toast("file_picker_cleared", "已清空，可重新选择");
                    }
                })
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();

        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                try {
                    if (pos == 0) {
                        File p = dir.getParentFile();
                        if (p != null && p.exists()) {
                            dlg.dismiss();
                            pickerDir = p;
                            showPickerDialog(p);
                        }
                        return;
                    }
                    File f = items.get(pos - 1);
                    if (f.isDirectory()) {
                        dlg.dismiss();
                        pickerDir = f;
                        showPickerDialog(f);
                    } else {
                        if (!pendingRefFiles.contains(f)) {
                            pendingRefFiles.add(f);
                        }
                    }
                } catch (Throwable t) {}
            }
        });
    }

    private void finishRefPick() {
        if (pendingRefFiles.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pendingRefFiles.size(); i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append("@").append(pendingRefFiles.get(i).getAbsolutePath()).append(" ");
            }
            Editable e = etInput.getText();
            String cur = e.toString();
            int idx = cur.lastIndexOf('@');
            if (idx >= 0) e.replace(idx, e.length(), sb.toString());
            else e.append(sb.toString());
            Toast.makeText(this, t("file_picker_ref", "已引用 %d 个文件").replace("%d", String.valueOf(pendingRefFiles.size())), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {}
        pendingRefFiles.clear();
    }

    // ============================================================
    // 生成草稿
    // ============================================================

    private void saveDraftThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastDraftTime < DRAFT_INTERVAL) return;
        lastDraftTime = now;
        saveDraft();
    }

    private void saveDraft() {
        try {
            if (messages == null || messages.length() == 0) return;
            JSONObject o = new JSONObject();
            o.put("sid", currentSessionId == null ? "" : currentSessionId);
            o.put("messages", messages.toString());
            o.put("content", roundContent.toString());
            o.put("reasoning", roundReasoning.toString());
            final String txt = o.toString();
            io.execute(new Runnable() { @Override public void run() { writeTextFile(new File(getFilesDir(), "draft.json"), txt); } });
        } catch (Throwable t) {}
    }

    private void clearDraft() {
        try {
            io.execute(new Runnable() { @Override public void run() { try { File f = new File(getFilesDir(), "draft.json"); if (f.exists()) f.delete(); } catch (Throwable t) {} } });
        } catch (Throwable t) {}
        lastDraftTime = 0L;
    }

    private boolean restoreDraftIfAny() {
        try {
            File f = new File(getFilesDir(), "draft.json");
            if (!f.exists()) return false;
            String txt = readTextFile(f, 20 * 1024 * 1024);
            if (txt == null || txt.trim().length() == 0) { try { f.delete(); } catch (Throwable t) {} return false; }
            JSONObject o = new JSONObject(txt);
            String sid = UiUtils.optStr(o, "sid");
            String msgsStr = o.optString("messages", "");
            String content = o.optString("content", "");
            String reasoning = o.optString("reasoning", "");
            if (msgsStr.length() > 0) messages = new JSONArray(msgsStr);
            if (sid.length() > 0) currentSessionId = sid;
            if (currentSessionId == null || currentSessionId.length() == 0) currentSessionId = "s" + System.currentTimeMillis();
            ensureSystemMessage();
            resetRound();
            roundContent = new StringBuilder(content);
            roundReasoning = new StringBuilder(reasoning);
            if (content.length() > 0 || reasoning.length() > 0) appendPartialAssistant();
            resetRound();
            try { f.delete(); } catch (Throwable t) {}
            return (content.length() > 0 || reasoning.length() > 0);
        } catch (Throwable t) { return false; }
    }

    // ============================================================
    // 用户消息编辑 / 重发 / 附件菜单
    // ============================================================

    private void showEditDialog(final int index) {
        if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
        if (index < 0 || index >= messages.length()) return;
        JSONObject m = messages.optJSONObject(index); if (m == null || !"user".equals(UiUtils.optStr(m, "role"))) return;
        final String oldText = UiUtils.contentToText(m, false);
        List<String> oldUrls = UiUtils.extractImageUrls(m);
        final String oldImage = oldUrls.isEmpty() ? null : UiUtils.resolveImageBase64(oldUrls.get(0));
        final EditText et = new EditText(this);
        if (oldText.length() > 0) { et.setText(oldText); et.setSelection(oldText.length()); }
        et.setTextSize(15); et.setGravity(Gravity.TOP | Gravity.LEFT); et.setMinLines(3); et.setMaxLines(8);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        et.setTextIsSelectable(true);

        final android.app.Dialog dlg = new GlassDialog.Builder(this)
                .setTitle(oldImage == null ? t("msg_edit_title", "修改我的消息") : t("msg_edit_title_with_image", "修改我的消息（含图片）"))
                .setView(et)
                .setPositiveButton(t("msg_resend", "重新发送"), null)
                .setNeutralButton(t("common_copy", "复制"), null)
                .setNegativeButton(t("common_cancel", "取消"), null)
                .show();

        TextView posBtn = GlassDialog.Builder.getButton(dlg, DialogInterface.BUTTON_POSITIVE);
        if (posBtn != null) {
            posBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String nt = et.getText().toString().trim();
                    if (nt.length() == 0 && oldImage == null) { toast("msg_empty", "内容不能为空"); return; }
                    dlg.dismiss(); resendFrom(index, nt, oldImage);
                }
            });
        }
        TextView neuBtn = GlassDialog.Builder.getButton(dlg, DialogInterface.BUTTON_NEUTRAL);
        if (neuBtn != null) {
            neuBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    MarkdownView.copyText(MainActivity.this, et.getText().toString());
                }
            });
        }
    }

    private void resendFrom(int index, String newText, String imageB64) {
        if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
        removeStoppedRow();
        while (messages.length() > index) messages.remove(messages.length() - 1);
        List<String> imgs = new ArrayList<String>();
        if (imageB64 != null && imageB64.length() > 0) imgs.add(imageB64);
        putUserMessage(expandAtReferences(newText), imgs);
        ensureSystemMessage(); fixDanglingToolCalls(); renderMessages(); startAgent();
    }

    private void putUserMessage(String text, List<String> imageB64List) {
        try {
            JSONObject m = new JSONObject(); m.put("role", "user");
            if (imageB64List == null || imageB64List.isEmpty()) m.put("content", text);
            else {
                JSONArray arr = new JSONArray();
                JSONObject t2 = new JSONObject(); t2.put("type", "text"); t2.put("text", text == null ? "" : text); arr.put(t2);
                for (int i = 0; i < imageB64List.size(); i++) {
                    JSONObject im = new JSONObject(); im.put("type", "image_url");
                    JSONObject u = new JSONObject(); u.put("url", "data:image/jpeg;base64," + imageB64List.get(i));
                    im.put("image_url", u); arr.put(im);
                }
                m.put("content", arr);
            }
            messages.put(m);
        } catch (Throwable t) {}
    }

    private void showAttachMenu() {
        List<String> items = new ArrayList<String>();
        items.add(t("attach_gallery", "从相册选择图片"));
        items.add(t("attach_camera", "拍照"));
        items.add(t("attach_ref_file", "引用本地文件（同 @）"));
        items.add(t("attach_import", "导入会话"));
        items.add(t("attach_realtime", "实时通话"));

        final List<JSONObject> inputPlugins;
        try {
            inputPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_INPUT_PLUS);
        } catch (Throwable t) {
            inputPlugins = new ArrayList<JSONObject>();
        }
        for (int i = 0; i < inputPlugins.size(); i++) {
            JSONObject p = inputPlugins.get(i);
            items.add(p.optString("title", p.optString("name", "插件")));
        }

        final List<String> fItems = items;
        GlassMenuDialog.showItems(this, t("attach_title", "添加内容"), fItems.toArray(new String[0]),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        String it = fItems.get(which);
                        if (t("attach_gallery", "从相册选择图片").equals(it)) pickFromGallery();
                        else if (t("attach_camera", "拍照").equals(it)) takePhoto();
                        else if (t("attach_ref_file", "引用本地文件（同 @）").equals(it)) showFilePicker();
                        else if (t("attach_import", "导入会话").equals(it)) showImportMenu();
                        else if (t("attach_realtime", "实时通话").equals(it)) startRealtimeCall();
                        else if (it != null) {
                            for (int k = 0; k < inputPlugins.size(); k++) {
                                JSONObject p = inputPlugins.get(k);
                                if (it.equals(p.optString("title", p.optString("name", "插件")))) {
                                    final JSONObject fp = p;
                                    UiPluginDialog.show(MainActivity.this, fp, new UiPluginDialog.OnSubmitListener() {
                                        @Override public void onSubmit(final JSONObject args) {
                                            runUiPluginInline(fp, args);
                                        }
                                    });
                                    break;
                                }
                            }
                        }
                    }
                });
    }

    private void startRealtimeCall() {
        try {
            String[] cfg = RealtimeConfig.resolve(this);
            if (cfg[0].length() == 0 || cfg[2].length() == 0 || cfg[1].length() == 0) {
                new GlassDialog.Builder(this)
                        .setTitle(t("realtime_not_configured_title", "实时通话未配置完整"))
                        .setMessage(t("realtime_not_configured_msg", "需要先配置「设置 → 语音 → 实时通话」里的 WebSocket 地址 / 模型 / API Key。\n\n是否现在去配置？"))
                        .setPositiveButton(t("realtime_go_config", "去配置"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    Intent it = new Intent(MainActivity.this, SettingsDetailActivity.class);
                                    it.putExtra(SettingsDetailActivity.EXTRA_CATEGORY, "realtime");
                                    startActivity(it);
                                } catch (Throwable t) {}
                                d.dismiss();
                            }
                        })
                        .setNegativeButton(t("common_cancel", "取消"), null)
                        .show();
                return;
            }
            Intent it = new Intent(this, VoiceCallActivity.class);
            it.putExtra(VoiceCallActivity.EXTRA_MODE, "voice");
            startActivity(it);
        } catch (Throwable t) {
            Toast.makeText(this, t("realtime_no_open", "无法打开通话界面: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFromGallery() {
        try { Intent it = new Intent(Intent.ACTION_GET_CONTENT); it.setType("image/*"); it.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(Intent.createChooser(it, t("attach_gallery", "从相册选择图片")), REQ_IMG_PICK); }
        catch (Throwable t) { Toast.makeText(this, t("attach_no_gallery", "无法打开相册: ") + t.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void takePhoto() {
        try {
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (it.resolveActivity(getPackageManager()) == null) { toast("attach_no_camera", "未找到相机应用"); return; }
            startActivityForResult(it, REQ_IMG_CAMERA);
        } catch (Throwable t) {
            Toast.makeText(this, t("attach_no_camera2", "无法打开相机: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // UI 插件执行
    // ============================================================

    private void runUiPluginInline(final JSONObject plugin, final JSONObject args) {
        try {
            final JSONObject action = plugin.optJSONObject("action");
            final String kind = action == null ? "prompt" : action.optString("kind", "prompt").toLowerCase();

            if ("script".equals(kind)) {
                new GlassDialog.Builder(this)
                        .setTitle(t("plugin_exec_script_title", "执行脚本？"))
                        .setMessage(t("plugin_exec_script_msg", "该插件将执行本机 shell 脚本。只在你信任插件来源时继续。"))
                        .setPositiveButton(t("plugin_exec", "执行"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                d.dismiss();
                                doRunUiPlugin(plugin, args, kind);
                            }
                        })
                        .setNegativeButton(t("common_cancel", "取消"), null)
                        .show();
                return;
            }
            doRunUiPlugin(plugin, args, kind);
        } catch (Throwable t) {
            Toast.makeText(this, "执行失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void doRunUiPlugin(final JSONObject plugin, final JSONObject args, final String kind) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = PluginManager.executeUiAction(MainActivity.this, plugin, args);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if ("prompt".equals(kind)) {
                            try {
                                if (etInput != null) {
                                    etInput.setText(result == null ? "" : result);
                                    etInput.setSelection(etInput.getText().length());
                                }
                            } catch (Throwable t) {}
                        } else if ("ui_change".equals(kind)) {
                            new GlassDialog.Builder(MainActivity.this)
                                    .setTitle("UI 已更新")
                                    .setMessage(result == null ? "（空）" : result)
                                    .setPositiveButton(t("common_know", "知道了"), new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int w) {
                                            d.dismiss();
                                            try { applyThemeChrome(); } catch (Throwable t) {}
                                            try { applyTopbarIcons(); } catch (Throwable t) {}
                                            try { renderMessages(); } catch (Throwable t) {}
                                        }
                                    })
                                    .show();
                        } else {
                            if (result != null && result.startsWith("错误:")) {
                                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            try {
                                String header = "【插件 " + plugin.optString("title", plugin.optString("name", "")) + "】\n";
                                putUserMessage(header + (result == null ? "" : result), null);
                                appendUserBubbleIncremental();
                                saveCurrentSession();
                                setGeneratingState(true);
                                shouldStop = false;
                                discardToolResults = false;
                                clearDraft();
                                fixDanglingToolCalls();
                                runAgentStep(0);
                            } catch (Throwable t) {}
                        }
                    }
                });
            }
        }).start();
    }

    // ============================================================
    // onActivityResult
    // ============================================================

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_VOICE_CALL) return;
            return;
        }
        if (requestCode == REQ_IMG_PICK) {
            List<Uri> uris = new ArrayList<Uri>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) {
                    Uri u = cd.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) { toast("image_none", "未选择图片"); return; }
            for (int i = 0; i < uris.size(); i++) prepareImageFromUri(uris.get(i));
        }
    }

    private void prepareImageFromUri(final Uri uri) {
        new Thread(new Runnable() { @Override public void run() {
            Bitmap bmp = decodeSampled(uri, 1280);
            if (bmp == null) { ui.post(new Runnable() { @Override public void run() { toast("image_read_fail", "图片读取失败"); } }); return; }
            prepareImageFromBitmap(bmp);
        }}).start();
    }

    private void prepareImageFromBitmap(final Bitmap src) {
        new Thread(new Runnable() { @Override public void run() {
            final String b64 = compressToBase64(src, 1280, 85);
            final byte[] bytes = compressToBytes(src, 1280, 85);
            final Bitmap thumb = makeThumb(src, 240);
            if (b64 == null || bytes == null) {
                ui.post(new Runnable() { @Override public void run() { toast("image_process_fail", "图片处理失败"); } });
                return;
            }
            ui.post(new Runnable() { @Override public void run() {
                if (!alive) return;
                pendingImageBase64List.add(b64);
                pendingImageBytesList.add(bytes);
                refreshImagePreview(thumb);
                int n = pendingImageBase64List.size();
                if (n == 1) toast("image_attached", "图片已附加，可继续添加或直接发送");
            }});
        }}).start();
    }

    private void refreshImagePreview(Bitmap newestThumb) {
        if (pendingImageBase64List.isEmpty()) {
            if (ivPreview != null) ivPreview.setImageDrawable(null);
            if (llImagePreview != null) llImagePreview.setVisibility(View.GONE);
            return;
        }
        if (newestThumb != null && ivPreview != null) ivPreview.setImageBitmap(newestThumb);
        if (llImagePreview != null) llImagePreview.setVisibility(View.VISIBLE);
        TextView cnt = (TextView) findViewById(R.id.tvPreviewCount);
        if (cnt != null) {
            int n = pendingImageBase64List.size();
            cnt.setText(n > 1 ? t("image_count", "已选 %d 张").replace("%d", String.valueOf(n)) : "");
        }
    }

    private void clearPendingImage() {
        pendingImageBase64List.clear();
        pendingImageBytesList.clear();
        pendingRefFiles.clear();
        refreshImagePreview(null);
    }

    private Bitmap decodeSampled(Uri uri, int maxPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(uri); if (is == null) return null;
            BitmapFactory.decodeStream(is, null, o); try { is.close(); } catch (Throwable t) {}
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1; while ((o.outWidth / sample) > maxPx * 2 || (o.outHeight / sample) > maxPx * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
            InputStream is2 = getContentResolver().openInputStream(uri); if (is2 == null) return null;
            Bitmap bm = BitmapFactory.decodeStream(is2, null, o2); try { is2.close(); } catch (Throwable t) {}
            return bm;
        } catch (Throwable t) { return null; }
    }

    private Bitmap scaleDown(Bitmap src, int maxPx) {
        if (src == null) return null; int w = src.getWidth(); int h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float ratio = (float) maxPx / (float) (w > h ? w : h);
        int nw = Math.max(1, (int) (w * ratio)); int nh = Math.max(1, (int) (h * ratio));
        try { return Bitmap.createScaledBitmap(src, nw, nh, true); } catch (Throwable t) { return src; }
    }

    private byte[] compressToBytes(Bitmap src, int maxPx, int quality) {
        try {
            Bitmap bm = scaleDown(src, maxPx);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            return bos.toByteArray();
        } catch (Throwable t) { return null; }
    }

    private String compressToBase64(Bitmap src, int maxPx, int quality) {
        byte[] bytes = compressToBytes(src, maxPx, quality); if (bytes == null) return null;
        try { return Base64.encodeToString(bytes, Base64.NO_WRAP); } catch (Throwable t) { return null; }
    }

    private Bitmap makeThumb(Bitmap src, int maxPx) { try { return scaleDown(src, maxPx); } catch (Throwable t) { return null; } }

    private String saveImageToFile(byte[] data) {
        if (data == null) return null; File dir = null;
        try {
            if (Environment.getExternalStorageDirectory() != null) {
                File d = new File(Environment.getExternalStorageDirectory(), "AI/images");
                if (!d.exists()) d.mkdirs();
                if (d.canWrite()) dir = d;
            }
        } catch (Throwable t) { dir = null; }
        if (dir == null) { dir = new File(getFilesDir(), "images"); if (!dir.exists()) dir.mkdirs(); }
        try {
            File f = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data); fos.close();
            return f.getAbsolutePath();
        } catch (Throwable t) { return null; }
    }

    private void loadThumbnail(final String b64, final ImageView iv) {
        new Thread(new Runnable() { @Override public void run() {
            Bitmap bm = null;
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1; while (o.outWidth > 0 && (o.outWidth / sample) > 480) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
                bm = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
            } catch (Throwable t) { bm = null; }
            if (bm == null) return;
            final Bitmap fb = bm;
            ui.post(new Runnable() { @Override public void run() {
                if (!alive || iv == null) { try { fb.recycle(); } catch (Throwable t) {} return; }
                try { iv.setImageBitmap(fb); } catch (Throwable t) {}
            }});
        }}).start();
    }

    private void scrollToBottom() {
        if (svMessages == null) return;
        svMessages.post(new Runnable() { @Override public void run() { try { svMessages.fullScroll(View.FOCUS_DOWN); } catch (Throwable t) {} } });
    }

    private void preprocessToolNames(Map<String, String> toolNames) {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null || !"assistant".equals(UiUtils.optStr(m, "role"))) continue;
                JSONArray tcs = m.optJSONArray("tool_calls");
                if (tcs == null) continue;
                for (int k = 0; k < tcs.length(); k++) {
                    JSONObject tc = tcs.optJSONObject(k);
                    if (tc == null) continue;
                    JSONObject fn = tc.optJSONObject("function");
                    String nm = fn == null ? "工具" : UiUtils.optStr(fn, "name");
                    if (nm.length() == 0) nm = "工具";
                    toolNames.put(UiUtils.optStr(tc, "id"), nm);
                }
            }
        } catch (Throwable t) {}
    }

    private void renderMessagesBatch(int start, int total, Map<String, String> toolNames) {
        final int batchSize = 20;
        int end = Math.min(total, start + batchSize);

        LinearLayout aiBox = null;
        int lastAssistantIdx = -1;
        try {
            for (int i = start; i < end; i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");

                if ("system".equals(role)) continue;

                if ("user".equals(role)) {
                    if (aiBox != null && lastAssistantIdx >= 0) {
                        addAiActionRow(aiBox, lastAssistantIdx);
                        lastAssistantIdx = -1;
                    }
                    aiBox = null;
                    addUserBubble(i, m);
                    continue;
                }

                if ("assistant".equals(role)) {
                    if (aiBox == null) aiBox = newAiBox(i);
                    lastAssistantIdx = i;

                    String reasoning = UiUtils.optStr(m, "reasoning_content");
                    if (reasoning.length() > 0) addReasoningPanel(aiBox, reasoning, 0);

                    JSONArray tcs = m.optJSONArray("tool_calls");
                    if (tcs != null) for (int k = 0; k < tcs.length(); k++) {
                        JSONObject tc = tcs.optJSONObject(k);
                        if (tc == null) continue;
                        JSONObject fn = tc.optJSONObject("function");
                        String nm = fn == null ? "工具" : UiUtils.optStr(fn, "name");
                        if (nm.length() == 0) nm = "工具";
                        addToolPanel(aiBox, nm, fn == null ? "" : UiUtils.optStr(fn, "arguments"), false);
                    }

                    String c = UiUtils.contentToText(m, true);
                    if (c.trim().length() > 0) addMarkdownBox(aiBox, c);
                    continue;
                }

                if ("tool".equals(role)) {
                    if (aiBox == null) aiBox = newAiBox(-1);
                    String nm = toolNames.get(UiUtils.optStr(m, "tool_call_id"));
                    String content = UiUtils.optStr(m, "content");
                    addToolPanel(aiBox, nm == null ? "工具结果" : nm, content, false);
                    continue;
                }
            }
            if (end >= total && aiBox != null && lastAssistantIdx >= 0) {
                addAiActionRow(aiBox, lastAssistantIdx);
                lastAssistantIdx = -1;
            }
        } catch (Throwable t) {
            addSystemNote("渲染消息时出错，已跳过部分内容");
            end = total;
        }

        if (end < total) {
            final int nextStart = end;
            final int finalTotal = total;
            final Map<String, String> finalToolNames = new HashMap<String, String>(toolNames);
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (alive) renderMessagesBatch(nextStart, finalTotal, finalToolNames);
                }
            }, 50);
        } else {
            scrollToBottom();
        }
    }

    private final BroadcastReceiver bgRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.ai.office.REFRESH_BG".equals(intent.getAction())) {
                applyChatBackground();
            }
        }
    };

    private void renderMessagesOptimized() {
        if (llMessages == null) return;
        llMessages.removeAllViews(); currentAiContainer = null; stoppedRow = null; resetRound();
        if (messages == null || messages.length() <= 1) { if (layoutWelcome != null) layoutWelcome.setVisibility(View.VISIBLE); return; }
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);

        int total = messages.length();
        int start = 0;
        if (total > renderLimit) {
            start = total - renderLimit;
            TextView more = new TextView(this);
            more.setText(t("main_load_earlier", "▲ 上方还有 %d 条更早的消息，点击加载").replace("%d", String.valueOf(start)));
            more.setTextSize(12);
            more.setTextColor(UiOverrides.primary(this));
            more.setGravity(Gravity.CENTER);
            more.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10));
            more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    renderLimit = renderLimit + MAX_RENDER_MESSAGES;
                    final int scrollY = svMessages.getScrollY();
                    renderMessages();
                    svMessages.postDelayed(new Runnable() {
                        @Override public void run() { svMessages.scrollTo(0, scrollY); }
                    }, 100);
                }
            });
            llMessages.addView(more);
        }

        Map<String, String> toolNames = new HashMap<String, String>();
        preprocessToolNames(toolNames);
        renderMessagesBatch(start, total, toolNames);
    }

    // ============================================================
    // 导入
    // ============================================================

    private void showImportMenu() {
        try {
            final String[] items = new String[]{
                    t("import_md", "导入Markdown会话"),
                    t("import_json", "导入JSON会话")};
            GlassMenuDialog.showItems(this, t("import_title", "导入会话"), items,
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            d.dismiss();
                            if (which == 0) importMarkdownSession();
                            else if (which == 1) importJsonSession();
                        }
                    });
        } catch (Throwable t) {
            Toast.makeText(this, t("err_no_import_menu", "打开导入菜单失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importMarkdownSession() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("text/*");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/markdown", "text/x-markdown", "text/plain", "application/octet-stream"});
            startActivityForResult(Intent.createChooser(it, t("import_choose_md", "选择Markdown文件")), REQ_IMPORT_MD);
        } catch (Throwable t) {
            Toast.makeText(this, t("err_no_file_picker", "无法打开文件选择器: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importJsonSession() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("application/json");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(it, t("import_choose_json", "选择JSON文件")), REQ_IMPORT_JSON);
        } catch (Throwable t) {
            Toast.makeText(this, t("err_no_file_picker", "无法打开文件选择器: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private JSONArray parseMarkdownToMessages(String markdown) {
        try {
            JSONArray msgs = new JSONArray();
            String[] lines = markdown.split("\n");
            StringBuilder currentContent = new StringBuilder();
            String currentRole = null;
            final String[] pendingReasoning = new String[]{""};

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                boolean boundary = false;
                String newRole = null;

                if (line.startsWith("## ")) {
                    boundary = true;
                    if (line.startsWith("## 我") || line.startsWith("## 用户")) newRole = "user";
                    else if (line.startsWith("## AI Office") || line.startsWith("## AI")) newRole = "assistant";
                    else newRole = "";
                } else if (line.startsWith("<details><summary>思考过程</summary>")) {
                    boundary = true; newRole = "思考过程";
                } else if (line.startsWith("<details><summary>工具结果</summary>")) {
                    boundary = true; newRole = "工具结果";
                }

                if (boundary) {
                    flushMdSection(msgs, currentRole, currentContent.toString(), pendingReasoning);
                    currentContent = new StringBuilder();
                    currentRole = newRole;
                    continue;
                }

                if (currentRole == null || currentRole.length() == 0) continue;
                if (line.equals("</details>")) continue;
                if ("思考过程".equals(currentRole) && line.startsWith("> ")) line = line.substring(2);
                currentContent.append(line).append("\n");
            }
            flushMdSection(msgs, currentRole, currentContent.toString(), pendingReasoning);
            return msgs;
        } catch (Throwable t) {
            Toast.makeText(this, t("err_md_parse", "解析Markdown失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
            return new JSONArray();
        }
    }

    private void flushMdSection(JSONArray msgs, String role, String content, String[] pendingReasoning) {
        String c = content == null ? "" : content.trim();
        if (c.length() == 0) return;
        try {
            if ("思考过程".equals(role)) {
                pendingReasoning[0] = pendingReasoning[0].length() == 0
                        ? c : pendingReasoning[0] + "\n\n" + c;
            } else if ("工具结果".equals(role)) {
                JSONObject msg = new JSONObject();
                msg.put("role", "assistant");
                msg.put("content", "【工具结果】\n" + wrapToolFence(c));
                msgs.put(msg);
            } else if ("assistant".equals(role)) {
                JSONObject msg = new JSONObject();
                msg.put("role", "assistant");
                if (pendingReasoning[0].length() > 0) {
                    msg.put("reasoning_content", pendingReasoning[0]);
                    pendingReasoning[0] = "";
                }
                msg.put("content", c);
                msgs.put(msg);
            } else if ("user".equals(role)) {
                pendingReasoning[0] = "";
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", c);
                msgs.put(msg);
            }
        } catch (Throwable t) {}
    }

    private String wrapToolFence(String c) {
        String body = c == null ? "" : c;
        try {
            if (body.startsWith("```") || body.startsWith("~~~")) {
                String fence = body.substring(0, 3);
                int firstNl = body.indexOf('\n');
                if (firstNl > 0 && body.endsWith(fence)) {
                    body = body.substring(firstNl + 1, body.length() - 3);
                    if (body.endsWith("\n")) body = body.substring(0, body.length() - 1);
                }
            }
        } catch (Throwable t) {}
        return "```\n" + body + "\n```";
    }

    private void loadImportedSession(JSONArray msgs) {
        if (isGenerating) { toast("main_generating_please_stop", "正在生成中，请先停止"); return; }
        if (msgs == null || msgs.length() == 0) {
            toast("err_import_empty", "导入的会话内容为空");
            return;
        }
        saveCurrentSession();
        messages = msgs;
        currentSessionId = "imported_" + System.currentTimeMillis();
        currentPromptTokens = 0;
        currentCompletionTokens = 0;
        currentCacheHitTokens = 0;
        updateTokenStatsUI();
        renderLimit = MAX_RENDER_MESSAGES;
        ensureSystemMessage();
        resetRound();
        currentAiContainer = null;
        stoppedRow = null;
        clearPendingImage();
        pendingToolIds = new JSONArray();
        discardToolResults = false;
        if (llMessages != null) llMessages.removeAllViews();
        if (layoutWelcome != null) layoutWelcome.setVisibility(View.GONE);
        renderMessages();
        saveCurrentSession();
        toast("err_import_ok", "会话导入成功");
    }
}
