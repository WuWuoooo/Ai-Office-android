package com.ai.office;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SettingsDetailActivity extends BaseActivity {

    public static final String EXTRA_CATEGORY = "category";

    private static final String PREFS = "ai_office_config";

    private SharedPreferences prefs;
    private LinearLayout container;
    private String category;

    private static final String[] THEME_VALUES = {"system", "light", "dark"};
    private static final String[] THEME_LABELS = {"跟随系统", "浅色", "深色"};

    private static final String[] FONT_VALUES = {"0", "0.85", "1.0", "1.15", "1.3", "1.5"};
    private static final String[] FONT_LABELS = {"跟随系统", "小", "标准", "大", "特大", "超大"};

    private static final String[] THINKING_IDS = {"", "none", "low", "high", "max"};
    private static final String[] THINKING_LABELS = {"默认（不发送）", "None（关闭思考）", "low（低）", "high（高）", "max（最高）"};

    private static final String[] TTS_FORMAT_VALUES = {"wav", "mp3", "pcm"};
    private static final String[] TTS_FORMAT_LABELS = {"wav", "mp3", "pcm（可能不支持播放）"};

    private static final String[] VISION_PROTOCOL_VALUES = {"", "openai", "anthropic"};
    private static final String[] VISION_PROTOCOL_LABELS = {"（同主配置）", "openai", "anthropic"};

    private static final String[] SEARCH_RECENCY_VALUES = {"noLimit", "oneDay", "oneWeek", "oneMonth", "oneYear"};
    private static final String[] SEARCH_RECENCY_LABELS = {"不限", "一天内", "一周内", "一个月内", "一年内"};

    private static final String[] READER_FORMAT_VALUES = {"markdown", "text"};
    private static final String[] READER_FORMAT_LABELS = {"markdown", "text"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_settings_detail);
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            category = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_CATEGORY);
            if (category == null) category = "";

            container = (LinearLayout) findViewById(R.id.llDetailContainer);
            TextView tvTitle = (TextView) findViewById(R.id.tvDetailTitle);
            TextView btnBack = (TextView) findViewById(R.id.btnBack);

            if (tvTitle != null) tvTitle.setText(titleOfCategory(category));
            if (btnBack != null) {
                btnBack.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { finish(); }
                });
            }

            buildCategory(category);
            onResume();
        } catch (Throwable t) {
            Toast.makeText(this, "设置页加载失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

private String titleOfCategory(String c) {
    if ("ai".equals(c)) return "AI 接入";
    if ("vision".equals(c)) return "识图 API";
    if ("search".equals(c)) return "联网搜索";
    if ("realtime".equals(c)) return "实时通话";
    if ("tts".equals(c)) return "语音合成";
    if ("generate".equals(c)) return "生成行为";
    if ("appearance".equals(c)) return "外观";
    if ("toggle".equals(c)) return "功能开关";
    if ("memory".equals(c)) return "长期记忆";
    if ("prompt".equals(c)) return "系统提示词";
    if ("plugin".equals(c)) return "插件";
    if ("permission".equals(c)) return "权限";
    if ("balance".equals(c)) return "余额查询";
    return "设置";
}

private void buildCategory(String c) {
    if ("ai".equals(c)) buildAi();
    else if ("vision".equals(c)) buildVision();
    else if ("search".equals(c)) buildSearch();
    else if ("realtime".equals(c)) buildRealtime();
    else if ("tts".equals(c)) buildTts();
    else if ("generate".equals(c)) buildGenerate();
    else if ("appearance".equals(c)) buildAppearance();
    else if ("toggle".equals(c)) buildToggle();
    else if ("memory".equals(c)) buildMemory();
    else if ("prompt".equals(c)) buildPrompt();
    else if ("plugin".equals(c)) buildPlugin();
    else if ("permission".equals(c)) buildPermission();
    else if ("balance".equals(c)) buildBalance();
}

    // ============================================================
    // 通用 UI 辅助
    // ============================================================

    private void addSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(UiOverrides.outline(this));
        tv.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 14),
                      UiUtils.dp(this, 4), UiUtils.dp(this, 8));
        container.addView(tv);
    }

    private LinearLayout addCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);
        container.addView(card);
        return card;
    }

    private void addHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(UiOverrides.outline(this));
        tv.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 6),
                      UiUtils.dp(this, 4), UiUtils.dp(this, 4));
        container.addView(tv);
    }

    private void addValueRow(LinearLayout card, String title, String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 12),
                       UiUtils.dp(this, 16), UiUtils.dp(this, 12));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(UiOverrides.onSurface(this));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tvTitle, tlp);

        TextView tvVal = new TextView(this);
        tvVal.setText(value == null ? "" : value);
        tvVal.setTextSize(13);
        tvVal.setTextColor(UiOverrides.outline(this));
        tvVal.setSingleLine(true);
        tvVal.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvVal.setMaxWidth(UiUtils.dp(this, 160));
        row.addView(tvVal);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(UiOverrides.outline(this));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.setMargins(UiUtils.dp(this, 6), 0, 0, 0);
        row.addView(arrow, alp);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        card.addView(row);
    }

    private void addSwitchRow(LinearLayout card, String title, boolean checked, final SwitchListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 8),
                       UiUtils.dp(this, 16), UiUtils.dp(this, 8));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(UiOverrides.onSurface(this));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tvTitle, tlp);

        final Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { l.onChanged(sw.isChecked()); }
        });
        row.addView(sw);
        card.addView(row);
    }

    public interface SwitchListener { void onChanged(boolean checked); }

    private void editText(String title, final String key, String def, final boolean password) {
        final EditText et = new EditText(this);
        et.setText(prefs.getString(key, def == null ? "" : def));
        et.setSelection(et.getText().length());
        et.setTextSize(15);
        if (password) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10),
                      UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    private void editMultiline(String title, final String key, String def) {
        final EditText et = new EditText(this);
        et.setText(prefs.getString(key, def == null ? "" : def));
        et.setSelection(et.getText().length());
        et.setTextSize(13);
        et.setGravity(Gravity.TOP | Gravity.LEFT);
        et.setMinLines(5);
        et.setMaxLines(15);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10),
                      UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    private void editNumber(String title, final String key, String def) {
        final EditText et = new EditText(this);
        et.setText(prefs.getString(key, def));
        et.setSelection(et.getText().length());
        et.setTextSize(15);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10),
                      UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) v = "0";
                        prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    private void pickSingle(String title, final String key, String[] values, String[] labels, String defValue) {
        String cur = prefs.getString(key, defValue);
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
        final String[] fV = values;
        final String[] fL = labels;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(fL, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = fV[which];
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null).create().show();
    }

    private String shortText(String s, String fallback) {
        if (s == null || s.trim().length() == 0) return fallback;
        s = s.trim();
        if (s.length() > 22) s = s.substring(0, 22) + "…";
        return s;
    }

    private String maskKey(String k) {
        if (k == null || k.length() == 0) return "（未设置）";
        if (k.length() <= 8) return "••••";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 4);
    }

    // ============================================================
    // AI 接入
    // ============================================================

    private void buildAi() {
        addSectionTitle("连接");
        LinearLayout c1 = addCard();

        addValueRow(c1, "AI 供应商", AIProvider.nameOf(prefs.getString("provider", "deepseek")),
                new Runnable() { @Override public void run() { pickProvider(); } });
        addValueRow(c1, "API 地址",
                shortText(prefs.getString("base_url", ""), "（未设置）"),
                new Runnable() { @Override public void run() { editText("API 地址", "base_url", "https://api.deepseek.com", false); } });
        addValueRow(c1, "API Key",
                maskKey(prefs.getString("api_key", "")),
                new Runnable() { @Override public void run() { editText("API Key", "api_key", "", true); } });
        addValueRow(c1, "默认模型",
                shortText(prefs.getString("model", "deepseek-flash"), "deepseek-flash"),
                new Runnable() { @Override public void run() { editText("默认模型", "model", "deepseek-flash", false); } });
        addValueRow(c1, "可切换的模型列表",
                shortText(prefs.getString("model_presets", ""), "（未设置）"),
                new Runnable() { @Override public void run() { editText("模型列表（英文逗号分隔）", "model_presets", "", false); } });

        addHint("可切换的模型列表用英文逗号分隔；顶栏点击模型名可快速切换。");

        addSectionTitle("协议");
        LinearLayout c2 = addCard();
        final String proto = prefs.getString("protocol", AIProvider.protocolOf(prefs.getString("provider", "deepseek")));
        addValueRow(c2, "协议", proto == null ? "openai" : proto,
                new Runnable() { @Override public void run() {
                    pickSingle("协议", "protocol", new String[]{"openai", "anthropic"}, new String[]{"openai", "anthropic"}, "openai");
                }});
        addHint("切换供应商时会自动设置协议，一般不用手动改。Anthropic 使用 /v1/messages，其余使用 /chat/completions。");
    }

    private void pickProvider() {
        final String[] ids = AIProvider.IDS;
        final String[] names = AIProvider.allNames();
        String cur = prefs.getString("provider", "deepseek");
        int sel = AIProvider.indexOf(cur);
        new AlertDialog.Builder(this)
                .setTitle("选择 AI 供应商")
                .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String id = ids[which];
                        prefs.edit().putString("provider", id).commit();
                        String url = AIProvider.urlOf(id);
                        if (url != null && url.length() > 0) prefs.edit().putString("base_url", url).commit();
                        String models = AIProvider.modelsOf(id);
                        if (models != null && models.length() > 0) prefs.edit().putString("model_presets", models).commit();
                        prefs.edit().putString("protocol", AIProvider.protocolOf(id)).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null).create().show();
    }

    // ============================================================
    // 识图 API
    // ============================================================

    private void buildVision() {
        addSectionTitle("识图模型");
        LinearLayout c1 = addCard();

        addValueRow(c1, "识图模型",
                shortText(prefs.getString("vision_model", ""), "（未设置）"),
                new Runnable() { @Override public void run() { editText("识图模型", "vision_model", "", false); } });
        addValueRow(c1, "识图 API 地址",
                shortText(prefs.getString("vision_base_url", ""), "（未设置）"),
                new Runnable() { @Override public void run() { editText("识图 API 地址", "vision_base_url", "", false); } });
        addValueRow(c1, "识图 API Key",
                maskKey(prefs.getString("vision_api_key", "")),
                new Runnable() { @Override public void run() { editText("识图 API Key", "vision_api_key", "", true); } });
        addValueRow(c1, "识图协议",
                shortText(prefs.getString("vision_protocol", ""), "（未设置）"),
                new Runnable() { @Override public void run() {
                    pickSingle("识图协议", "vision_protocol", VISION_PROTOCOL_VALUES, VISION_PROTOCOL_LABELS, "");
                }});

        addHint("留空即可，留空的项目会自动使用 AI 接入里的配置。当发送含图片的消息时，AI 会切换到识图模型。");
    }

    // ============================================================
    // 联网搜索
    // ============================================================

    private void buildSearch() {
        addSectionTitle("搜索引擎");
        LinearLayout c1 = addCard();

        addValueRow(c1, "搜索提供商",
                SearchProvider.nameOf(prefs.getString("search_provider", "bing")),
                new Runnable() { @Override public void run() { pickSearchProvider(); } });

        final String pid = prefs.getString("search_provider", "bing");
        final boolean needsKey = SearchProvider.needsKey(pid);

        if (needsKey) {
            addValueRow(c1, "搜索 API Key",
                    maskKey(prefs.getString("search_api_key", "")),
                    new Runnable() { @Override public void run() { editText("搜索 API Key", "search_api_key", "", true); } });
        }

        addValueRow(c1, "请求地址（可改）",
                shortText(prefs.getString("search_override_url_" + pid, ""),
                          shortText(SearchProvider.urlOf(pid), "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText("请求地址（留空用内置）", "search_override_url_" + pid, "", false);
                }});

        if ("POST".equals(SearchProvider.methodOf(pid)) || "custom".equals(pid)) {
            addValueRow(c1, "请求体模板",
                    shortText(prefs.getString("search_override_body_" + pid, ""),
                              shortText(SearchProvider.bodyOf(pid), "（未设置）")),
                    new Runnable() { @Override public void run() {
                        editMultiline("请求体模板（留空用内置）", "search_override_body_" + pid, "");
                    }});
        }

        addValueRow(c1, "返回条数",
                prefs.getString("search_count", "10"),
                new Runnable() { @Override public void run() { editNumber("返回条数（1-50）", "search_count", "10"); } });

        addSectionTitle("网页阅读（fetch_url）");
        LinearLayout c2 = addCard();
        addValueRow(c2, "返回格式",
                prefs.getString("reader_return_format", "markdown"),
                new Runnable() { @Override public void run() {
                    pickSingle("网页阅读返回格式", "reader_return_format", READER_FORMAT_VALUES, READER_FORMAT_LABELS, "markdown");
                }});

        if ("custom".equals(pid)) {
            addSectionTitle("自定义提供商参数");
            LinearLayout c3 = addCard();
            addValueRow(c3, "请求方法",
                    prefs.getString("search_custom_method", "GET"),
                    new Runnable() { @Override public void run() {
                        pickSingle("请求方法", "search_custom_method",
                                new String[]{"GET", "POST"}, new String[]{"GET", "POST"}, "GET");
                    }});
            addValueRow(c3, "请求地址",
                    shortText(prefs.getString("search_custom_url", ""), "（未设置）"),
                    new Runnable() { @Override public void run() {
                        editText("请求地址（支持 {{query}} {{count}} {{api_key}}）", "search_custom_url", "", false);
                    }});
            addValueRow(c3, "请求体",
                    shortText(prefs.getString("search_custom_body", ""), "（未设置）"),
                    new Runnable() { @Override public void run() {
                        editMultiline("请求体（支持 {{query_raw}} {{count}} {{api_key}}）", "search_custom_body", "");
                    }});
            addValueRow(c3, "鉴权头名称",
                    shortText(prefs.getString("search_custom_auth_header", ""), "（无）"),
                    new Runnable() { @Override public void run() {
                        editText("鉴权头名称（如 Authorization）", "search_custom_auth_header", "", false);
                    }});
            addValueRow(c3, "鉴权头前缀",
                    shortText(prefs.getString("search_custom_auth_prefix", ""), "（无）"),
                    new Runnable() { @Override public void run() {
                        editText("鉴权头前缀（如 Bearer ）", "search_custom_auth_prefix", "", false);
                    }});
            addValueRow(c3, "结果数组路径",
                    shortText(prefs.getString("search_custom_result_path", ""), "（未设置）"),
                    new Runnable() { @Override public void run() {
                        editText("结果数组路径（如 web.results）", "search_custom_result_path", "", false);
                    }});
            addValueRow(c3, "标题字段", shortText(prefs.getString("search_custom_title_key", "title"), "title"),
                    new Runnable() { @Override public void run() { editText("标题字段", "search_custom_title_key", "title", false); } });
            addValueRow(c3, "内容字段", shortText(prefs.getString("search_custom_content_key", "content"), "content"),
                    new Runnable() { @Override public void run() { editText("内容字段", "search_custom_content_key", "content", false); } });
            addValueRow(c3, "链接字段", shortText(prefs.getString("search_custom_link_key", "url"), "url"),
                    new Runnable() { @Override public void run() { editText("链接字段", "search_custom_link_key", "url", false); } });
        }

        addHint("Bing / DuckDuckGo 免费无需 Key。其余需要 Key 的提供商，Key 可填在「搜索 API Key」里。占位符：{{query}} URL 编码，{{query_raw}} 不编码，{{count}} 条数，{{api_key}} Key。");
    }

// ============================================================
// 实时通话（GLM-Realtime / OpenAI Realtime）
// ============================================================

private void buildRealtime() {
    addSectionTitle("实时通话模型");
    LinearLayout c1 = addCard();

    final String curProvider = prefs.getString("realtime_provider", "glm-realtime");

    addValueRow(c1, "模型供应商", RealtimeConfig.nameOf(curProvider),
            new Runnable() { @Override public void run() { pickRealtimeProvider(); } });
    addValueRow(c1, "WebSocket 地址",
            shortText(prefs.getString("realtime_url", ""),
                      shortText(RealtimeConfig.urlOf(curProvider), "（未设置）")),
            new Runnable() { @Override public void run() {
                editText("WebSocket 地址（留空用内置）", "realtime_url", "", false);
            }});
    addValueRow(c1, "模型名",
            shortText(prefs.getString("realtime_model", ""),
                      shortText(RealtimeConfig.modelOf(curProvider), "（未设置）")),
            new Runnable() { @Override public void run() {
                editText("模型名（留空用内置）", "realtime_model", "", false);
            }});
    addValueRow(c1, "API Key",
            maskKey(prefs.getString("realtime_api_key", "")),
            new Runnable() { @Override public void run() {
                editText("实时通话 API Key（留空用主配置）", "realtime_api_key", "", true);
            }});
    addValueRow(c1, "协议",
            shortText(prefs.getString("realtime_protocol", ""),
                      shortText(RealtimeConfig.protocolOf(curProvider), "openai")),
            new Runnable() { @Override public void run() {
                pickSingle("协议", "realtime_protocol",
                        new String[]{"", "openai", "zhipu"},
                        new String[]{"（同供应商）", "openai", "zhipu"},
                        "");
            }});

    final String curVoices = RealtimeConfig.voicesOf(curProvider);
    if (curVoices != null && curVoices.length() > 0) {
        addValueRow(c1, "音色", shortText(prefs.getString("realtime_voice", ""),
                                          shortText(curVoices, "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText("音色（如 " + curVoices + "）", "realtime_voice", "", false);
                }});
    } else {
        addValueRow(c1, "音色", shortText(prefs.getString("realtime_voice", ""), "（未设置）"),
                new Runnable() { @Override public void run() {
                    editText("音色", "realtime_voice", "", false);
                }});
    }

    addHint("地址 / 模型 / Key 留空时用供应商预设；Key 若也为空，则回退到「AI 接入」里的 Key。"
            + "\nGLM-Realtime 官方地址：wss://open.bigmodel.cn/api/paas/v4/realtime，模型 glm-realtime-flash，音色 tongtong/chuchui/xiaochen/jam/wangjia。"
            + "\nOpenAI Realtime 官方地址：wss://api.openai.com/v1/realtime，模型 gpt-4o-realtime-preview，音色 alloy/echo/shimmer/verse。");
}

private void pickRealtimeProvider() {
    final String[] ids = RealtimeConfig.IDS;
    final String[] names = RealtimeConfig.allNames();
    String cur = prefs.getString("realtime_provider", "glm-realtime");
    int sel = RealtimeConfig.indexOf(cur);
    new AlertDialog.Builder(this)
            .setTitle("选择实时通话模型供应商")
            .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String id = ids[which];
                    prefs.edit().putString("realtime_provider", id).commit();
                    // 切换供应商时清空用户覆盖，让预设生效
                    prefs.edit().remove("realtime_url").commit();
                    prefs.edit().remove("realtime_model").commit();
                    prefs.edit().remove("realtime_protocol").commit();
                    prefs.edit().remove("realtime_voice").commit();
                    d.dismiss();
                    recreate();
                }
            })
            .setNegativeButton("取消", null).create().show();
}

    private void pickSearchProvider() {
        String cur = prefs.getString("search_provider", "bing");
        int sel = SearchProvider.indexOf(cur);
        new AlertDialog.Builder(this)
                .setTitle("选择搜索引擎")
                .setSingleChoiceItems(SearchProvider.allNames(), sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.edit().putString("search_provider", SearchProvider.IDS[which]).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null).create().show();
    }

    // ============================================================
    // TTS 语音合成
    // ============================================================

    private void buildTts() {
        addSectionTitle("语音合成");
        LinearLayout c1 = addCard();

        addSwitchRow(c1, "启用 TTS 朗读",
                prefs.getBoolean("tts_enabled", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("tts_enabled", checked).commit();
                    }
                });
        addSwitchRow(c1, "自动朗读 AI 回复",
                prefs.getBoolean("tts_auto_read", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("tts_auto_read", checked).commit();
                    }
                });
        addValueRow(c1, "TTS API 地址",
                shortText(prefs.getString("tts_api_url", ""), "（未设置，用系统 TTS）"),
                new Runnable() { @Override public void run() {
                    editText("TTS API 地址（留空用系统 TTS）", "tts_api_url", "https://open.bigmodel.cn/api/paas/v4/audio/speech", false);
                }});
        addValueRow(c1, "TTS API Key",
                maskKey(prefs.getString("tts_api_key", "")),
                new Runnable() { @Override public void run() { editText("TTS API Key", "tts_api_key", "", true); } });
        addValueRow(c1, "模型", shortText(prefs.getString("tts_model", "glm-tts"), "glm-tts"),
                new Runnable() { @Override public void run() { editText("TTS 模型", "tts_model", "glm-tts", false); } });
        addValueRow(c1, "音色 voice", shortText(prefs.getString("tts_voice", "tongtong"), "tongtong"),
                new Runnable() { @Override public void run() { editText("TTS 音色", "tts_voice", "tongtong", false); } });
        addValueRow(c1, "返回格式",
                prefs.getString("tts_format", "wav"),
                new Runnable() { @Override public void run() {
                    pickSingle("TTS 返回格式", "tts_format", TTS_FORMAT_VALUES, TTS_FORMAT_LABELS, "wav");
                }});
        addValueRow(c1, "语速 speed", shortText(prefs.getString("tts_speed", ""), "（默认）"),
                new Runnable() { @Override public void run() { editText("语速（留空不发送）", "tts_speed", "", false); } });

        addHint("「启用 TTS 朗读」控制总开关；「自动朗读 AI 回复」只控制每轮回答后是否自动读，关闭它仍可用消息末尾的「朗读」按钮手动读。\n智谱 glm-tts 示例：地址 https://open.bigmodel.cn/api/paas/v4/audio/speech，模型 glm-tts，音色 tongtong，格式 wav。留空 API 地址则使用系统 TTS。");
    }

    // ============================================================
    // 生成行为
    // ============================================================

    private void buildGenerate() {
        addSectionTitle("思考与工具");
        LinearLayout c1 = addCard();

        addValueRow(c1, "思考深度", thinkingLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle("思考深度", "thinking_effort", THINKING_IDS, THINKING_LABELS, "");
                }});
        addValueRow(c1, "最大工具调用轮数", prefs.getString("max_tool_rounds", "0"),
                new Runnable() { @Override public void run() { editNumber("最大轮数（0 = 不限制）", "max_tool_rounds", "0"); } });
        addValueRow(c1, "自动重试次数", prefs.getString("retry_times", "2"),
                new Runnable() { @Override public void run() { editNumber("重试次数", "retry_times", "2"); } });
        addValueRow(c1, "删除文件确认秒数", prefs.getString("delete_confirm_sec", "5"),
                new Runnable() { @Override public void run() { editNumber("删除确认秒数（0 = 不确认）", "delete_confirm_sec", "5"); } });
                
        addValueRow(c1, "启动时打开", startupLabel(),
        new Runnable() { @Override public void run() {
            pickSingle("启动时打开", "startup_mode",
                    new String[]{"last", "new"},
                    new String[]{"上次对话", "新对话"},
                    "last");
        }});

        addSectionTitle("上下文");
        LinearLayout c2 = addCard();
        addSwitchRow(c2, "自动压缩上下文",
                prefs.getBoolean("auto_compress", true),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("auto_compress", checked).commit();
                    }
                });
        addValueRow(c2, "每多少轮压缩一次", prefs.getString("compress_rounds", "10"),
                new Runnable() { @Override public void run() { editNumber("每多少轮压缩一次", "compress_rounds", "10"); } });

        addHint("思考深度选「None」会让支持关闭思考的模型不返回思考内容；最大轮数填 0 表示不限制。");
    }

    private String thinkingLabel() {
        String cur = prefs.getString("thinking_effort", "");
        for (int i = 0; i < THINKING_IDS.length; i++) {
            if (THINKING_IDS[i].equals(cur)) return THINKING_LABELS[i];
        }
        return THINKING_LABELS[0];
    }
    
    private String startupLabel() {
        String cur = prefs.getString("startup_mode", "last");
        if ("new".equals(cur)) return "新对话";
        return "上次对话";
    }

    // ============================================================
    // 外观
    // ============================================================

    private void buildAppearance() {
        addSectionTitle("外观");
        LinearLayout c1 = addCard();

        addValueRow(c1, "主题风格", themeLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle("主题风格（重启后完全生效）", "theme_style", THEME_VALUES, THEME_LABELS, "system");
                }});
        addValueRow(c1, "字体大小", fontLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle("字体大小", "font_scale", FONT_VALUES, FONT_LABELS, "0");
                }});
        addValueRow(c1, "聊天背景图",
                (prefs.getString("chat_bg_path", "") != null && prefs.getString("chat_bg_path", "").length() > 0) ? "已设置" : "未设置",
                new Runnable() { @Override public void run() { pickBackground(); } });
    }

    private String themeLabel() {
        String cur = prefs.getString("theme_style", "system");
        for (int i = 0; i < THEME_VALUES.length; i++) {
            if (THEME_VALUES[i].equals(cur)) return THEME_LABELS[i];
        }
        return THEME_LABELS[0];
    }

    private String fontLabel() {
        String cur = prefs.getString("font_scale", "0");
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (FONT_VALUES[i].equals(cur)) return FONT_LABELS[i];
        }
        return FONT_LABELS[0];
    }

    private void pickBackground() {
        final String cur = prefs.getString("chat_bg_path", "");
        String[] items;
        if (cur == null || cur.length() == 0) items = new String[]{"从相册选择图片"};
        else items = new String[]{"从相册选择图片", "清除背景图"};
        new AlertDialog.Builder(this)
                .setTitle("聊天背景图")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) {
                            try {
                                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                                it.setType("image/*");
                                it.addCategory(Intent.CATEGORY_OPENABLE);
                                startActivityForResult(Intent.createChooser(it, "选择聊天背景图"), 8001);
                            } catch (Throwable t) {
                                Toast.makeText(SettingsDetailActivity.this, "无法打开相册", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            prefs.edit().putString("chat_bg_path", "").commit();
                            sendBroadcast(new Intent("com.ai.office.REFRESH_BG"));
                            Toast.makeText(SettingsDetailActivity.this, "已清除", Toast.LENGTH_SHORT).show();
                            recreate();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 8001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                if (in == null) throw new Exception("无法读取所选图片");
                java.io.File dst = new java.io.File(getFilesDir(), "chat_bg.jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                prefs.edit().putString("chat_bg_path", dst.getAbsolutePath()).commit();
                Toast.makeText(this, "已设置背景图", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent("com.ai.office.REFRESH_BG"));
                recreate();
            } catch (Throwable t) {
                Toast.makeText(this, "背景图保存失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============================================================
    // 功能开关
    // ============================================================

    private void buildToggle() {
        addSectionTitle("行为开关");
        LinearLayout c1 = addCard();

        addSwitchRow(c1, "后台完成提醒",
                prefs.getBoolean("notify_done", true),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("notify_done", checked).commit();
                    }
                });
        addSwitchRow(c1, "图片保存为本地路径发送",
                prefs.getBoolean("image_as_file", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("image_as_file", checked).commit();
                    }
                });

        addSectionTitle("高级权限");
        LinearLayout c2 = addCard();

        addSwitchRow(c2, "允许 script 类型插件",
                prefs.getBoolean("allow_script_plugins", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_script_plugins", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? "已开启 script 插件，重启 App 后生效" : "已关闭 script 插件",
                                Toast.LENGTH_SHORT).show();
                    }
                });
        addSwitchRow(c2, "允许 AI 执行命令（shell / JS）",
                prefs.getBoolean("allow_shell_tool", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_shell_tool", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? "已开启，返回主界面即生效" : "已关闭命令执行工具",
                                Toast.LENGTH_SHORT).show();
                    }
                });
        addSwitchRow(c2, "允许 AI 操控手机（无障碍）",
                prefs.getBoolean("allow_accessibility_tool", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_accessibility_tool", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? "已开启。请到「权限」里开启无障碍服务" : "已关闭手机操控工具",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        addHint("script 插件能执行 shell 命令，只在你信任插件来源时开启。");
    }

    // ============================================================
    // 长期记忆
    // ============================================================

    private void buildMemory() {
        addSectionTitle("长期记忆");
        LinearLayout c1 = addCard();
        int count = MemoryStore.getAllKeys(this).size();
        addValueRow(c1, "管理记忆条目", count + " 条",
                new Runnable() { @Override public void run() { showMemoryManager(); } });
        addHint("AI 每次对话都会参考这些记忆；可在对话框里手动添加，也可点条目删除。");
    }

    private void showMemoryManager() {
        final List<String[]> entries = MemoryStore.getAllEntries(this);
        final List<String> items = new ArrayList<String>();
        for (int i = 0; i < entries.size(); i++) {
            String[] e = entries.get(i);
            String preview = e[1].replace('\n', ' ');
            if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
            items.add(e[0] + "：" + preview);
        }
        items.add("＋ 新增记忆");

        new AlertDialog.Builder(this)
                .setTitle("长期记忆（共 " + entries.size() + " 条）")
                .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == items.size() - 1) addOrEditMemory(null);
                        else showMemoryDetail(entries.get(which)[0], entries.get(which)[1]);
                    }
                })
                .setNegativeButton("关闭", null)
                .create().show();
    }

    private void showMemoryDetail(final String key, String content) {
        final String[] ops = new String[]{"编辑", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(key)
                .setMessage(content.length() > 2000 ? content.substring(0, 2000) + "…" : content)
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) addOrEditMemory(key);
                        else {
                            new AlertDialog.Builder(SettingsDetailActivity.this)
                                    .setTitle("删除记忆 " + key + "？")
                                    .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dd, int w) {
                                            MemoryStore.delete(SettingsDetailActivity.this, key);
                                            dd.dismiss();
                                            Toast.makeText(SettingsDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                            recreate();
                                        }
                                    })
                                    .setNegativeButton("取消", null).create().show();
                        }
                    }
                })
                .setNegativeButton("关闭", null).create().show();
    }

    private void addOrEditMemory(final String oldKey) {
        final EditText etKey = new EditText(this);
        etKey.setHint("记忆标识（英文/数字，如 user_pref）");
        etKey.setTextSize(15);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        etKey.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        if (oldKey != null) etKey.setText(oldKey);

        final EditText etVal = new EditText(this);
        etVal.setHint("记忆内容");
        etVal.setTextSize(15);
        etVal.setGravity(Gravity.TOP | Gravity.LEFT);
        etVal.setMinLines(3);
        etVal.setMaxLines(10);
        etVal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etVal.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        if (oldKey != null) {
            try {
                List<String[]> es = MemoryStore.getAllEntries(this);
                for (int i = 0; i < es.size(); i++) {
                    if (oldKey.equals(es.get(i)[0])) { etVal.setText(es.get(i)[1]); break; }
                }
            } catch (Throwable t) {}
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 12), UiUtils.dp(this, 16), UiUtils.dp(this, 12));
        wrap.addView(etKey, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(etVal, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(oldKey == null ? "新增记忆" : "编辑记忆")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String k = etKey.getText().toString().trim();
                        String v = etVal.getText().toString().trim();
                        if (k.length() == 0) { Toast.makeText(SettingsDetailActivity.this, "标识不能为空", Toast.LENGTH_SHORT).show(); return; }
                        if (oldKey != null && !oldKey.equals(k)) {
                            MemoryStore.delete(SettingsDetailActivity.this, oldKey);
                        }
                        MemoryStore.save(SettingsDetailActivity.this, k, v);
                        d.dismiss();
                        Toast.makeText(SettingsDetailActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    // ============================================================
    // 系统提示词
    // ============================================================

    private void buildPrompt() {
        addSectionTitle("系统提示词");
        LinearLayout c1 = addCard();
        String sp = prefs.getString("system_prompt", "");
        addValueRow(c1, "编辑系统提示词", (sp == null || sp.length() == 0) ? "默认" : "已自定义",
                new Runnable() { @Override public void run() { editSystemPrompt(); } });
        addHint("留空则使用内置默认提示词。");
    }

    private void editSystemPrompt() {
        final EditText et = new EditText(this);
        et.setText(prefs.getString("system_prompt", ""));
        et.setSelection(et.getText().length());
        et.setTextSize(13);
        et.setGravity(Gravity.TOP | Gravity.LEFT);
        et.setMinLines(6);
        et.setMaxLines(20);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 6), UiUtils.dp(this, 12), UiUtils.dp(this, 6));
        wrap.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("系统提示词（留空使用默认）")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        prefs.edit().putString("system_prompt", v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    // ============================================================
    // 插件（含 UI 覆盖管理）
    // ============================================================

    private void buildPlugin() {
        addSectionTitle("已加载的插件");
        LinearLayout c1 = addCard();
        addValueRow(c1, "查看已加载的插件", "", new Runnable() { @Override public void run() { showPlugins(); } });

        // settings_item 扩展点的 UI 插件动作
        List<JSONObject> uiPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_SETTINGS_ITEM);
        if (!uiPlugins.isEmpty()) {
            addSectionTitle("UI 插件动作");
            LinearLayout c2 = addCard();
            for (int i = 0; i < uiPlugins.size(); i++) {
                final JSONObject p = uiPlugins.get(i);
                String title = p.optString("title", p.optString("name", "插件"));
                addValueRow(c2, title, "", new Runnable() {
                    @Override public void run() {
                        UiPluginDialog.show(SettingsDetailActivity.this, p, new UiPluginDialog.OnSubmitListener() {
                            @Override public void onSubmit(JSONObject args) {
                                runSettingsPlugin(p, args);
                            }
                        });
                    }
                });
            }
        }

        // ★ UI 覆盖管理
        addSectionTitle("UI 覆盖（插件配色）");
        LinearLayout c3 = addCard();
        int cnt = UiOverrides.countOverrides(this);
        addValueRow(c3, "查看当前 UI 覆盖", cnt == 0 ? "（无）" : (cnt + " 项"),
                new Runnable() { @Override public void run() { showUiOverrides(); } });
        addValueRow(c3, "清除所有 UI 覆盖", "", new Runnable() { @Override public void run() { confirmClearUiOverrides(); } });

        addHint("把 .json 插件放在 /sdcard/AI/plugins/ 下，重启 App 即生效。extension 为 settings_item 的 UI 插件会出现在上方。\naction.kind = \"ui_change\" 的插件可以覆盖 App 配色，见 bubble_theme.json 示例。");
    }

    private void showUiOverrides() {
        try {
            String list = UiOverrides.listOverrides(this);
            new AlertDialog.Builder(this)
                    .setTitle("当前 UI 覆盖")
                    .setMessage(list)
                    .setPositiveButton("关闭", null)
                    .create().show();
        } catch (Throwable t) {
            Toast.makeText(this, "读取失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearUiOverrides() {
        new AlertDialog.Builder(this)
                .setTitle("清除所有 UI 覆盖？")
                .setMessage("将把 App 的颜色 / 尺寸恢复为默认值（不影响其它设置）。\n\n提示：清除后回到主界面即可看到效果。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        UiOverrides.clearAll(SettingsDetailActivity.this);
                        d.dismiss();
                        Toast.makeText(SettingsDetailActivity.this, "已清除，返回主界面生效", Toast.LENGTH_LONG).show();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .create().show();
    }

    private void showPlugins() {
        try {
            List<JSONObject> aiPlugins = PluginManager.loadPlugins(this);
            List<JSONObject> uiPlugins = PluginManager.loadUiPlugins(this);
            StringBuilder sb = new StringBuilder();
            sb.append("AI 工具插件：").append(aiPlugins.size()).append(" 个\n");
            sb.append("UI 扩展插件：").append(uiPlugins.size()).append(" 个\n\n");

            if (!aiPlugins.isEmpty()) {
                sb.append("── AI 工具插件 ──\n");
                for (int i = 0; i < aiPlugins.size(); i++) {
                    JSONObject p = aiPlugins.get(i);
                    sb.append("• ").append(p.optString("name", ""))
                      .append(" [").append(p.optString("_type", "prompt")).append("]\n");
                    String d = p.optString("description", "");
                    if (d.length() > 0) sb.append("  ").append(d).append("\n");
                }
                sb.append("\n");
            }
            if (!uiPlugins.isEmpty()) {
                sb.append("── UI 扩展插件 ──\n");
                for (int i = 0; i < uiPlugins.size(); i++) {
                    JSONObject p = uiPlugins.get(i);
                    sb.append("• ").append(p.optString("title", p.optString("name", "")))
                      .append(" → ").append(p.optString("extension", ""))
                      .append("\n");
                    String d = p.optString("description", "");
                    if (d.length() > 0) sb.append("  ").append(d).append("\n");
                }
                sb.append("\n");
            }

            sb.append("插件目录: /sdcard/AI/plugins/\n");
            sb.append("extension 可选：main_menu / message_long_press / input_plus / settings_item / toolbar\n");
            sb.append("action.kind 可选：prompt / http / script / ui_change");

            new AlertDialog.Builder(this)
                    .setTitle("已加载的插件")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", null)
                    .create().show();
        } catch (Throwable t) {
            Toast.makeText(this, "读取插件失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runSettingsPlugin(final JSONObject plugin, final JSONObject args) {
        try {
            final JSONObject action = plugin.optJSONObject("action");
            final String kind = action == null ? "prompt" : action.optString("kind", "prompt").toLowerCase();

            if ("script".equals(kind)) {
                new AlertDialog.Builder(this)
                        .setTitle("执行脚本？")
                        .setMessage("该插件将执行本机 shell 脚本。只在你信任插件来源时继续。")
                        .setPositiveButton("执行", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                d.dismiss();
                                doRunSettingsPlugin(plugin, args);
                            }
                        })
                        .setNegativeButton("取消", null).create().show();
                return;
            }
            doRunSettingsPlugin(plugin, args);
        } catch (Throwable t) {
            Toast.makeText(this, "执行失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void doRunSettingsPlugin(final JSONObject plugin, final JSONObject args) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = PluginManager.executeUiAction(SettingsDetailActivity.this, plugin, args);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(SettingsDetailActivity.this)
                                .setTitle("插件结果")
                                .setMessage(result == null ? "（空）" : result)
                                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        MarkdownView.copyText(SettingsDetailActivity.this, result);
                                        d.dismiss();
                                    }
                                })
                                .setNegativeButton("关闭", null)
                                .create().show();
                    }
                });
            }
        }).start();
    }

    // ============================================================
    // 权限
    // ============================================================

    private void buildPermission() {
        addSectionTitle("系统权限");
        LinearLayout c1 = addCard();

        addValueRow(c1, "申请「所有文件访问权限」", "", new Runnable() {
            @Override public void run() { openAllFilesSettings(); }
        });
        addValueRow(c1, "去开启「无障碍服务」", "", new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Throwable t) { Toast.makeText(SettingsDetailActivity.this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show(); }
            }
        });

        addHint("Android 11 及以上需要「所有文件访问权限」才能读写 /sdcard；AI 操控手机需要无障碍服务。");
    }

    private void openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, "当前系统版本无需此权限", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent it = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            it.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(it); }
            catch (Throwable t) { startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")); }
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开权限设置页", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // 余额查询
    // ============================================================

    private void buildBalance() {
        addSectionTitle("余额查询");
        LinearLayout c1 = addCard();

        addValueRow(c1, "余额查询接口",
                shortText(prefs.getString("balance_url", ""), "（未设置）"),
                new Runnable() { @Override public void run() { editText("余额查询接口 URL", "balance_url", "", false); } });
        addValueRow(c1, "立即查询", "", new Runnable() { @Override public void run() { queryBalance(); } });

        addHint("一般用各供应商提供的余额接口，例如 DeepSeek 的 /user/balance。");
    }

    private void queryBalance() {
        final String url = prefs.getString("balance_url", "").trim();
        final String key = prefs.getString("api_key", "").trim();
        if (url.length() == 0) {
            Toast.makeText(this, "请先设置「余额查询接口」", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在查询…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = doQueryBalance(url, key);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(SettingsDetailActivity.this)
                                .setTitle("余额查询结果")
                                .setMessage(result)
                                .setPositiveButton("知道了", null)
                                .create().show();
                    }
                });
            }
        }).start();
    }

    private String doQueryBalance(String url, String key) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            if (key != null && key.length() > 0) conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            if (code != 200) return "HTTP " + code + "\n" + clip(sb.toString(), 400);
            return clip(sb.toString(), 1500);
        } catch (Throwable t) {
            return "查询失败: " + t.getMessage();
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...(已截断)";
    }
}
