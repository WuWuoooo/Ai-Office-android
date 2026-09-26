package com.ai.office;

import android.app.Dialog;
import android.content.Context;
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
    private static final int REQ_IMPORT_LANG = 8002;

    private SharedPreferences prefs;
    private LinearLayout container;
    private String category;

    private static final String[] THEME_VALUES = {"system", "light", "dark"};
    private static final String[] FONT_VALUES = {"0", "0.85", "1.0", "1.15", "1.3", "1.5"};
    private static final String[] THINKING_IDS = {"", "none", "low", "high", "max"};
    private static final String[] TTS_FORMAT_VALUES = {"wav", "mp3", "pcm"};
    private static final String[] VISION_PROTOCOL_VALUES = {"", "openai", "anthropic"};
    private static final String[] SEARCH_RECENCY_VALUES = {"noLimit", "oneDay", "oneWeek", "oneMonth", "oneYear"};
    private static final String[] READER_FORMAT_VALUES = {"markdown", "text"};

    private static final char LF = (char) 10;

    private String[] themeLabels() {
        Context c = this;
        return new String[]{
                LanguageManager.t(c, "appearance_theme_system", "跟随系统"),
                LanguageManager.t(c, "appearance_theme_light", "浅色"),
                LanguageManager.t(c, "appearance_theme_dark", "深色")};
    }
    private String[] fontLabels() {
        Context c = this;
        return new String[]{
                LanguageManager.t(c, "appearance_font_system", "跟随系统"),
                LanguageManager.t(c, "appearance_font_small", "小"),
                LanguageManager.t(c, "appearance_font_standard", "标准"),
                LanguageManager.t(c, "appearance_font_large", "大"),
                LanguageManager.t(c, "appearance_font_xl", "特大"),
                LanguageManager.t(c, "appearance_font_xxl", "超大")};
    }
    private String[] thinkingLabels() {
        return new String[]{"默认（不发送）", "None（关闭思考）", "low（低）", "high（高）", "max（最高）"};
    }
    private String[] ttsFormatLabels() {
        return new String[]{"wav", "mp3", "pcm（可能不支持播放）"};
    }
    private String[] visionProtocolLabels() {
        return new String[]{"（同主配置）", "openai", "anthropic"};
    }
    private String[] readerFormatLabels() {
        return new String[]{"markdown", "text"};
    }

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
            Toast.makeText(this, "Settings load failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String titleOfCategory(String c) {
        Context ctx = this;
        if ("ai".equals(c)) return LanguageManager.t(ctx, "ai_title", "AI 接入");
        if ("vision".equals(c)) return LanguageManager.t(ctx, "vision_title", "识图 API");
        if ("search".equals(c)) return LanguageManager.t(ctx, "search_title", "联网搜索");
        if ("realtime".equals(c)) return LanguageManager.t(ctx, "realtime_title", "实时通话");
        if ("tts".equals(c)) return LanguageManager.t(ctx, "tts_title", "语音合成");
        if ("generate".equals(c)) return LanguageManager.t(ctx, "gen_title", "生成行为");
        if ("appearance".equals(c)) return LanguageManager.t(ctx, "appearance_title", "外观");
        if ("toggle".equals(c)) return LanguageManager.t(ctx, "toggle_title", "功能开关");
        if ("memory".equals(c)) return LanguageManager.t(ctx, "memory_title", "长期记忆");
        if ("prompt".equals(c)) return LanguageManager.t(ctx, "prompt_title", "系统提示词");
        if ("plugin".equals(c)) return LanguageManager.t(ctx, "plugin_title", "插件");
        if ("permission".equals(c)) return LanguageManager.t(ctx, "permission_title", "权限");
        if ("balance".equals(c)) return LanguageManager.t(ctx, "balance_title", "余额查询");
        if ("language".equals(c)) return LanguageManager.t(ctx, "lang_title", "语言");
if ("experimental".equals(c)) return LanguageManager.t(ctx, "experimental_title", "实验性功能");
if ("about".equals(c)) return LanguageManager.t(ctx, "about_title", "关于");
if ("imagegen".equals(c)) return LanguageManager.t(ctx, "imagegen_title", "文生图");
return LanguageManager.t(ctx, "settings", "设置");
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
        else if ("language".equals(c)) buildLanguage();
    else if ("experimental".equals(c)) buildExperimental();
    else if ("about".equals(c)) buildAbout();
    else if ("imagegen".equals(c)) buildImageGen();
}
    
    // ============================================================
// 关于
// ============================================================

private void buildAbout() {
    final Context ctx = this;

    addSectionTitle(LanguageManager.t(ctx, "about_section_app", "应用信息"));
    LinearLayout c1 = addCard();
    addValueRow(c1, LanguageManager.t(ctx, "about_app_name", "应用名"), "Ai Office",
            new Runnable() { @Override public void run() {} });
    addValueRow(c1, LanguageManager.t(ctx, "about_version", "版本号"), "v1.3.0",
            new Runnable() { @Override public void run() {} });
    addValueRow(c1, LanguageManager.t(ctx, "about_package", "包名"), "com.ai.office",
            new Runnable() { @Override public void run() {} });
    addValueRow(c1, LanguageManager.t(ctx, "about_author", "作者"), "WuWuoooo",
            new Runnable() { @Override public void run() {} });

    addSectionTitle(LanguageManager.t(ctx, "about_section_links", "相关链接"));
    LinearLayout c2 = addCard();
    final String githubUrl = "https://github.com/WuWuoooo/Ai-Office-android";
    addValueRow(c2, LanguageManager.t(ctx, "about_github", "GitHub 仓库"),
            "WuWuoooo/Ai-Office-android",
            new Runnable() {
                @Override public void run() {
                    try {
                        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
                        startActivity(it);
                    } catch (Throwable t) {
                        Toast.makeText(SettingsDetailActivity.this,
                                LanguageManager.t(SettingsDetailActivity.this, "about_open_link_fail", "无法打开链接"),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
    addValueRow(c2, LanguageManager.t(ctx, "about_license", "开源声明"),
            LanguageManager.t(ctx, "about_license_value", "零第三方依赖"),
            new Runnable() { @Override public void run() {} });
    addHint(LanguageManager.t(ctx, "about_desc",
            "Ai Office 是一款运行在 Android 上的 AI 办公助手。"));

    addSectionTitle(LanguageManager.t(ctx, "about_section_language", "语言"));
    LinearLayout c3 = addCard();
    addValueRow(c3, LanguageManager.t(ctx, "about_language", "当前语言"),
            currentLanguageLabel(),
            new Runnable() {
                @Override public void run() { showLanguagePicker(); }
            });
    addHint(LanguageManager.t(ctx, "about_language_hint",
            "点击可切换语言；设置页立即生效，其他页面下次进入时生效。"));

    addSectionTitle(LanguageManager.t(ctx, "about_section_thanks", "致谢"));
    LinearLayout c4 = addCard();
    addHint(LanguageManager.t(ctx, "about_thanks_text",
            "感谢所有为 AI Office 提供反馈、建议与测试的用户。"));
}

// ============================================================
// 文生图
// ============================================================

private void buildImageGen() {
    Context ctx = this;
    addSectionTitle(LanguageManager.t(ctx, "imagegen_section", "文生图模型"));
    LinearLayout c1 = addCard();

    final String curProvider = prefs.getString("image_gen_provider", "zhipu");

    addValueRow(c1, LanguageManager.t(ctx, "imagegen_provider", "供应商"),
            ImageGenConfig.nameOf(curProvider),
            new Runnable() { @Override public void run() { pickImageGenProvider(); } });
    addValueRow(c1, LanguageManager.t(ctx, "imagegen_base_url", "API 地址"),
            shortText(prefs.getString("image_gen_base_url", ""),
                      shortText(ImageGenConfig.urlOf(curProvider), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
            new Runnable() { @Override public void run() {
                editText(LanguageManager.t(SettingsDetailActivity.this, "imagegen_edit_base_url", "文生图 API 地址（留空用预设）"),
                        "image_gen_base_url", "", false);
            }});
    addValueRow(c1, LanguageManager.t(ctx, "imagegen_api_key", "API Key"),
            maskKey(prefs.getString("image_gen_api_key", "")),
            new Runnable() { @Override public void run() {
                editText(LanguageManager.t(SettingsDetailActivity.this, "imagegen_edit_api_key", "文生图 API Key（留空用主配置）"),
                        "image_gen_api_key", "", true);
            }});
    addValueRow(c1, LanguageManager.t(ctx, "imagegen_model", "模型"),
            shortText(prefs.getString("image_gen_model", ""),
                      shortText(ImageGenConfig.modelOf(curProvider), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
            new Runnable() { @Override public void run() {
                editText(LanguageManager.t(SettingsDetailActivity.this, "imagegen_edit_model", "文生图模型（留空用预设）"),
                        "image_gen_model", "", false);
            }});
    addValueRow(c1, LanguageManager.t(ctx, "imagegen_size", "默认尺寸"),
            shortText(prefs.getString("image_gen_size", ""),
                      firstSize(ImageGenConfig.sizesOf(curProvider))),
            new Runnable() { @Override public void run() {
                String[] sizes = ImageGenConfig.sizesOf(curProvider);
                if (sizes == null || sizes.length == 0) sizes = new String[]{"1024x1024"};
                pickSingle(LanguageManager.t(SettingsDetailActivity.this, "imagegen_size", "默认尺寸"),
                        "image_gen_size", sizes, sizes, "1024x1024");
            }});

    addHint(LanguageManager.t(ctx, "imagegen_hint",
            "留空的项会自动使用 AI 接入里的配置。")
          + (char) 10
          + "智谱 CogView：地址 https://open.bigmodel.cn/api/paas/v4，模型 cogview-4。"
          + (char) 10
          + "OpenAI DALL-E：地址 https://api.openai.com/v1，模型 dall-e-3。"
          + (char) 10
          + "SiliconFlow：地址 https://api.siliconflow.cn/v1，模型 Kwai-Kolors/Kolors。");
}

private String firstSize(String[] arr) {
    if (arr == null || arr.length == 0) return "1024x1024";
    return arr[0];
}

private void pickImageGenProvider() {
    final String[] ids = ImageGenConfig.IDS;
    final String[] names = ImageGenConfig.allNames();
    String cur = prefs.getString("image_gen_provider", "zhipu");
    final int sel = ImageGenConfig.indexOf(cur);
    GlassMenuDialog.showItems(this,
            LanguageManager.t(this, "imagegen_pick_provider", "选择文生图供应商"),
            withCheck(names, sel),
            new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                    String id = ids[which];
                    prefs.edit().putString("image_gen_provider", id).commit();
                    prefs.edit().remove("image_gen_base_url").commit();
                    prefs.edit().remove("image_gen_model").commit();
                    prefs.edit().remove("image_gen_size").commit();
                    recreate();
                }
            });
}

private String currentLanguageLabel() {
    try {
        String id = LanguageManager.getCurrentId(this);
        if (LanguageManager.LANG_ZH.equals(id)) return "简体中文";
        if (LanguageManager.LANG_EN.equals(id)) return "English";
        List<String[]> cs = LanguageManager.listCustom(this);
        for (int i = 0; i < cs.size(); i++) {
            if (cs.get(i)[0].equals(id)) return cs.get(i)[1];
        }
        return id;
    } catch (Throwable t) { return "简体中文"; }
}

private void showLanguagePicker() {
    try {
        final String curId = LanguageManager.getCurrentId(this);
        final List<String> labels = new ArrayList<String>();
        final List<String> ids = new ArrayList<String>();

        List<String[]> builtin = LanguageManager.listBuiltin();
        for (int i = 0; i < builtin.size(); i++) {
            String id = builtin.get(i)[0];
            String name = builtin.get(i)[1];
            labels.add(name + (id.equals(curId) ? "  ✓" : ""));
            ids.add(id);
        }
        List<String[]> custom = LanguageManager.listCustom(this);
        for (int i = 0; i < custom.size(); i++) {
            String id = custom.get(i)[0];
            String name = custom.get(i)[1];
            labels.add(name + (id.equals(curId) ? "  ✓" : ""));
            ids.add(id);
        }

        final String[] fIds = ids.toArray(new String[0]);
        final String[] fLabels = labels.toArray(new String[0]);

        GlassMenuDialog.showItems(this,
                LanguageManager.t(this, "lang_title", "语言"),
                fLabels,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which >= 0 && which < fIds.length) {
                            LanguageManager.setCurrentId(SettingsDetailActivity.this, fIds[which]);
                            Toast.makeText(SettingsDetailActivity.this,
                                    LanguageManager.t(SettingsDetailActivity.this, "lang_switched", "已切换语言"),
                                    Toast.LENGTH_SHORT).show();
                            recreate();
                        }
                    }
                });
    } catch (Throwable t) {
        Toast.makeText(this, "打开语言列表失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
    }
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
    if (UiOverrides.glassEnabled(this)) {
        final LinearLayout fCard = card;
        card.post(new Runnable() {
            @Override public void run() {
                try {
                    android.graphics.drawable.Drawable d = UiOverrides.cardBgDrawable(SettingsDetailActivity.this, fCard);
                    if (d != null) fCard.setBackgroundDrawable(d);
                } catch (Throwable t) {}
            }
        });
    } else {
        card.setBackgroundResource(R.drawable.card_bg);
    }
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

        new GlassDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton(LanguageManager.t(this, "common_save", "保存"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
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

        new GlassDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton(LanguageManager.t(this, "common_save", "保存"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
    }

    private void editNumber(String title, final String key, String def) {
        final EditText et = new EditText(this);
        et.setText(prefs.getString(key, def));
        et.setSelection(et.getText().length());
        et.setTextSize(15);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10),
                      UiUtils.dp(this, 12), UiUtils.dp(this, 10));

        new GlassDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton(LanguageManager.t(this, "common_save", "保存"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        if (v.length() == 0) v = "0";
                        prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
    }

    private void pickSingle(String title, final String key, String[] values, String[] labels, String defValue) {
        String cur = prefs.getString(key, defValue);
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
        final String[] fV = values;
        new GlassDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = fV[which];
                        if (v.length() == 0) prefs.edit().remove(key).commit();
                        else prefs.edit().putString(key, v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
    }

    private String shortText(String s, String fallback) {
        if (s == null || s.trim().length() == 0) return fallback;
        s = s.trim();
        if (s.length() > 22) s = s.substring(0, 22) + "…";
        return s;
    }

    private String maskKey(String k) {
        if (k == null || k.length() == 0) return LanguageManager.t(this, "ai_unset", "（未设置）");
        if (k.length() <= 8) return "••••";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 4);
    }

    // ============================================================
    // AI 接入
    // ============================================================

    private void buildAi() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "ai_section_connection", "连接"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "ai_provider", "AI 供应商"),
                AIProvider.nameOf(prefs.getString("provider", "deepseek")),
                new Runnable() { @Override public void run() { pickProvider(); } });
        addValueRow(c1, LanguageManager.t(ctx, "ai_base_url", "API 地址"),
                shortText(prefs.getString("base_url", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "ai_edit_base_url", "API 地址"),
                            "base_url", "https://api.deepseek.com", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "ai_api_key", "API Key"),
                maskKey(prefs.getString("api_key", "")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "ai_edit_api_key", "API Key"),
                            "api_key", "", true);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "ai_default_model", "默认模型"),
                shortText(prefs.getString("model", "deepseek-flash"), "deepseek-flash"),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "ai_edit_model", "默认模型"),
                            "model", "deepseek-flash", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "ai_model_presets", "可切换的模型列表"),
                shortText(prefs.getString("model_presets", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "ai_edit_presets", "模型列表（英文逗号分隔）"),
                            "model_presets", "", false);
                }});

        addHint(LanguageManager.t(ctx, "ai_hint_presets",
                "可切换的模型列表用英文逗号分隔；顶栏点击模型名可快速切换。"));

        addSectionTitle(LanguageManager.t(ctx, "ai_section_protocol", "协议"));
        LinearLayout c2 = addCard();
        final String proto = prefs.getString("protocol", AIProvider.protocolOf(prefs.getString("provider", "deepseek")));
        addValueRow(c2, LanguageManager.t(ctx, "ai_protocol", "协议"), proto == null ? "openai" : proto,
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "ai_protocol", "协议"), "protocol",
                            new String[]{"openai", "anthropic"}, new String[]{"openai", "anthropic"}, "openai");
                }});
        addHint(LanguageManager.t(ctx, "ai_hint_protocol",
                "切换供应商时会自动设置协议，一般不用手动改。Anthropic 使用 /v1/messages，其余使用 /chat/completions。"));
    }

    private String[] withCheck(String[] labels, int selectedIdx) {
        if (labels == null) return new String[0];
        String[] out = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            out[i] = (i == selectedIdx ? "✓ " : "   ") + labels[i];
        }
        return out;
    }

    private void pickProvider() {
        final String[] ids = AIProvider.IDS;
        final String[] names = AIProvider.allNames();
        String cur = prefs.getString("provider", "deepseek");
        int sel = AIProvider.indexOf(cur);
        GlassMenuDialog.showItems(this,
                LanguageManager.t(this, "ai_pick_provider", "选择 AI 供应商"),
                withCheck(names, sel),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        String id = ids[which];
                        prefs.edit().putString("provider", id).commit();
                        String url = AIProvider.urlOf(id);
                        if (url != null && url.length() > 0) prefs.edit().putString("base_url", url).commit();
                        String models = AIProvider.modelsOf(id);
                        if (models != null && models.length() > 0) prefs.edit().putString("model_presets", models).commit();
                        prefs.edit().putString("protocol", AIProvider.protocolOf(id)).commit();
                        recreate();
                    }
                });
    }

    // ============================================================
    // 识图 API
    // ============================================================

    private void buildVision() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "vision_section", "识图模型"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "vision_model", "识图模型"),
                shortText(prefs.getString("vision_model", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "vision_edit_model", "识图模型"),
                            "vision_model", "", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "vision_base_url", "识图 API 地址"),
                shortText(prefs.getString("vision_base_url", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "vision_edit_base_url", "识图 API 地址"),
                            "vision_base_url", "", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "vision_api_key", "识图 API Key"),
                maskKey(prefs.getString("vision_api_key", "")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "vision_edit_api_key", "识图 API Key"),
                            "vision_api_key", "", true);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "vision_protocol", "识图协议"),
                shortText(prefs.getString("vision_protocol", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "vision_pick_protocol", "识图协议"),
                            "vision_protocol", VISION_PROTOCOL_VALUES, visionProtocolLabels(), "");
                }});

        addHint(LanguageManager.t(ctx, "vision_hint",
                "留空即可，留空的项目会自动使用 AI 接入里的配置。当发送含图片的消息时，AI 会切换到识图模型。"));
    }

    // ============================================================
    // 联网搜索
    // ============================================================

    private void buildSearch() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "search_section_engine", "搜索引擎"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "search_provider", "搜索提供商"),
                SearchProvider.nameOf(prefs.getString("search_provider", "bing")),
                new Runnable() { @Override public void run() { pickSearchProvider(); } });

        final String pid = prefs.getString("search_provider", "bing");
        final boolean needsKey = SearchProvider.needsKey(pid);

        if (needsKey) {
            addValueRow(c1, LanguageManager.t(ctx, "search_api_key", "搜索 API Key"),
                    maskKey(prefs.getString("search_api_key", "")),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_api_key", "搜索 API Key"),
                                "search_api_key", "", true);
                    }});
        }

        addValueRow(c1, LanguageManager.t(ctx, "search_override_url", "请求地址（可改）"),
                shortText(prefs.getString("search_override_url_" + pid, ""),
                          shortText(SearchProvider.urlOf(pid), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_override_url", "请求地址（留空用内置）"),
                            "search_override_url_" + pid, "", false);
                }});

        if ("POST".equals(SearchProvider.methodOf(pid)) || "custom".equals(pid)) {
            addValueRow(c1, LanguageManager.t(ctx, "search_override_body", "请求体模板"),
                    shortText(prefs.getString("search_override_body_" + pid, ""),
                              shortText(SearchProvider.bodyOf(pid), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
                    new Runnable() { @Override public void run() {
                        editMultiline(LanguageManager.t(SettingsDetailActivity.this, "search_edit_override_body", "请求体模板（留空用内置）"),
                                "search_override_body_" + pid, "");
                    }});
        }

        addValueRow(c1, LanguageManager.t(ctx, "search_count", "返回条数"),
                prefs.getString("search_count", "10"),
                new Runnable() { @Override public void run() {
                    editNumber(LanguageManager.t(SettingsDetailActivity.this, "search_edit_count", "返回条数（1-50）"),
                            "search_count", "10");
                }});

        addSectionTitle(LanguageManager.t(ctx, "search_section_reader", "网页阅读（fetch_url）"));
        LinearLayout c2 = addCard();
        addValueRow(c2, LanguageManager.t(ctx, "search_reader_format", "返回格式"),
                prefs.getString("reader_return_format", "markdown"),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "search_reader_format", "返回格式"),
                            "reader_return_format", READER_FORMAT_VALUES, readerFormatLabels(), "markdown");
                }});

        if ("custom".equals(pid)) {
            addSectionTitle(LanguageManager.t(ctx, "search_section_custom", "自定义提供商参数"));
            LinearLayout c3 = addCard();
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_method", "请求方法"),
                    prefs.getString("search_custom_method", "GET"),
                    new Runnable() { @Override public void run() {
                        pickSingle(LanguageManager.t(SettingsDetailActivity.this, "search_custom_method", "请求方法"),
                                "search_custom_method", new String[]{"GET", "POST"}, new String[]{"GET", "POST"}, "GET");
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_url", "请求地址"),
                    shortText(prefs.getString("search_custom_url", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_url", "请求地址（支持 {{query}} {{count}} {{api_key}}）"),
                                "search_custom_url", "", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_body", "请求体"),
                    shortText(prefs.getString("search_custom_body", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                    new Runnable() { @Override public void run() {
                        editMultiline(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_body", "请求体（支持 {{query_raw}} {{count}} {{api_key}}）"),
                                "search_custom_body", "");
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_auth_header", "鉴权头名称"),
                    shortText(prefs.getString("search_custom_auth_header", ""), "（无）"),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_auth_header", "鉴权头名称（如 Authorization）"),
                                "search_custom_auth_header", "", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_auth_prefix", "鉴权头前缀"),
                    shortText(prefs.getString("search_custom_auth_prefix", ""), "（无）"),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_auth_prefix", "鉴权头前缀（如 Bearer ）"),
                                "search_custom_auth_prefix", "", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_result_path", "结果数组路径"),
                    shortText(prefs.getString("search_custom_result_path", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_result_path", "结果数组路径（如 web.results）"),
                                "search_custom_result_path", "", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_title_key", "标题字段"),
                    shortText(prefs.getString("search_custom_title_key", "title"), "title"),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_title_key", "标题字段"),
                                "search_custom_title_key", "title", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_content_key", "内容字段"),
                    shortText(prefs.getString("search_custom_content_key", "content"), "content"),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_content_key", "内容字段"),
                                "search_custom_content_key", "content", false);
                    }});
            addValueRow(c3, LanguageManager.t(ctx, "search_custom_link_key", "链接字段"),
                    shortText(prefs.getString("search_custom_link_key", "url"), "url"),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "search_edit_custom_link_key", "链接字段"),
                                "search_custom_link_key", "url", false);
                    }});
        }

        addHint(LanguageManager.t(ctx, "search_hint",
                "Bing / DuckDuckGo 免费无需 Key。其余需要 Key 的提供商，Key 可填在「搜索 API Key」里。"
              + "占位符：{{query}} URL 编码，{{query_raw}} 不编码，{{count}} 条数，{{api_key}} Key。"));
    }

    private void pickSearchProvider() {
        String cur = prefs.getString("search_provider", "bing");
        final int sel = SearchProvider.indexOf(cur);
        final String[] ids = SearchProvider.IDS;
        final String[] names = SearchProvider.allNames();
        GlassMenuDialog.showItems(this,
                LanguageManager.t(this, "search_pick_provider", "选择搜索引擎"),
                withCheck(names, sel),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        prefs.edit().putString("search_provider", ids[which]).commit();
                        recreate();
                    }
                });
    }

    // ============================================================
    // 实时通话
    // ============================================================

    private void buildRealtime() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "realtime_section", "实时通话模型"));
        LinearLayout c1 = addCard();

        final String curProvider = prefs.getString("realtime_provider", "glm-realtime");

        addValueRow(c1, LanguageManager.t(ctx, "realtime_provider", "模型供应商"),
                RealtimeConfig.nameOf(curProvider),
                new Runnable() { @Override public void run() { pickRealtimeProvider(); } });
        addValueRow(c1, LanguageManager.t(ctx, "realtime_url", "WebSocket 地址"),
                shortText(prefs.getString("realtime_url", ""),
                          shortText(RealtimeConfig.urlOf(curProvider), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "realtime_edit_url", "WebSocket 地址（留空用内置）"),
                            "realtime_url", "", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "realtime_model", "模型名"),
                shortText(prefs.getString("realtime_model", ""),
                          shortText(RealtimeConfig.modelOf(curProvider), LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "realtime_edit_model", "模型名（留空用内置）"),
                            "realtime_model", "", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "realtime_api_key", "API Key"),
                maskKey(prefs.getString("realtime_api_key", "")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "realtime_edit_key", "实时通话 API Key（留空用主配置）"),
                            "realtime_api_key", "", true);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "realtime_protocol", "协议"),
                shortText(prefs.getString("realtime_protocol", ""),
                          shortText(RealtimeConfig.protocolOf(curProvider), "openai")),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "realtime_protocol", "协议"),
                            "realtime_protocol",
                            new String[]{"", "openai", "zhipu"},
                            new String[]{"（同供应商）", "openai", "zhipu"},
                            "");
                }});

        final String curVoices = RealtimeConfig.voicesOf(curProvider);
        if (curVoices != null && curVoices.length() > 0) {
            addValueRow(c1, LanguageManager.t(ctx, "realtime_voice", "音色"),
                    shortText(prefs.getString("realtime_voice", ""), shortText(curVoices, LanguageManager.t(ctx, "ai_unset", "（未设置）"))),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "realtime_edit_voice", "音色") + "（" + curVoices + "）",
                                "realtime_voice", "", false);
                    }});
        } else {
            addValueRow(c1, LanguageManager.t(ctx, "realtime_voice", "音色"),
                    shortText(prefs.getString("realtime_voice", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                    new Runnable() { @Override public void run() {
                        editText(LanguageManager.t(SettingsDetailActivity.this, "realtime_edit_voice", "音色"),
                                "realtime_voice", "", false);
                    }});
        }

        addHint(LanguageManager.t(ctx, "realtime_hint",
                "地址 / 模型 / Key 留空时用供应商预设；Key 若也为空，则回退到「AI 接入」里的 Key。"
              + LF + "GLM-Realtime 官方地址：wss://open.bigmodel.cn/api/paas/v4/realtime，模型 glm-realtime-flash，音色 tongtong/chuchui/xiaochen/jam/wangjia。"
              + LF + "OpenAI Realtime 官方地址：wss://api.openai.com/v1/realtime，模型 gpt-4o-realtime-preview，音色 alloy/echo/shimmer/verse。"));
    }

    private void pickRealtimeProvider() {
        final String[] ids = RealtimeConfig.IDS;
        final String[] names = RealtimeConfig.allNames();
        String cur = prefs.getString("realtime_provider", "glm-realtime");
        final int sel = RealtimeConfig.indexOf(cur);
        GlassMenuDialog.showItems(this,
                LanguageManager.t(this, "realtime_pick_provider", "选择实时通话模型供应商"),
                withCheck(names, sel),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        String id = ids[which];
                        prefs.edit().putString("realtime_provider", id).commit();
                        prefs.edit().remove("realtime_url").commit();
                        prefs.edit().remove("realtime_model").commit();
                        prefs.edit().remove("realtime_protocol").commit();
                        prefs.edit().remove("realtime_voice").commit();
                        recreate();
                    }
                });
    }

    // ============================================================
    // TTS
    // ============================================================

    private void buildTts() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "tts_section", "语音合成"));
        LinearLayout c1 = addCard();

        addSwitchRow(c1, LanguageManager.t(ctx, "tts_enabled", "启用 TTS 朗读"),
                prefs.getBoolean("tts_enabled", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("tts_enabled", checked).commit();
                    }
                });
        addSwitchRow(c1, LanguageManager.t(ctx, "tts_auto_read", "自动朗读 AI 回复"),
                prefs.getBoolean("tts_auto_read", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("tts_auto_read", checked).commit();
                    }
                });
        addValueRow(c1, LanguageManager.t(ctx, "tts_api_url", "TTS API 地址"),
                shortText(prefs.getString("tts_api_url", ""), LanguageManager.t(ctx, "tts_unset", "（未设置，用系统 TTS）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "tts_edit_url", "TTS API 地址（留空用系统 TTS）"),
                            "tts_api_url", "https://open.bigmodel.cn/api/paas/v4/audio/speech", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "tts_api_key", "TTS API Key"),
                maskKey(prefs.getString("tts_api_key", "")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "tts_edit_key", "TTS API Key"),
                            "tts_api_key", "", true);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "tts_model", "模型"),
                shortText(prefs.getString("tts_model", "glm-tts"), "glm-tts"),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "tts_edit_model", "TTS 模型"),
                            "tts_model", "glm-tts", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "tts_voice", "音色 voice"),
                shortText(prefs.getString("tts_voice", "tongtong"), "tongtong"),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "tts_edit_voice", "TTS 音色"),
                            "tts_voice", "tongtong", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "tts_format", "返回格式"),
                prefs.getString("tts_format", "wav"),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "tts_pick_format", "TTS 返回格式"),
                            "tts_format", TTS_FORMAT_VALUES, ttsFormatLabels(), "wav");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "tts_speed", "语速 speed"),
                shortText(prefs.getString("tts_speed", ""), LanguageManager.t(ctx, "tts_speed_default", "（默认）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "tts_edit_speed", "语速（留空不发送）"),
                            "tts_speed", "", false);
                }});

        addHint(LanguageManager.t(ctx, "tts_hint",
                "「启用 TTS 朗读」控制总开关；「自动朗读 AI 回复」只控制每轮回答后是否自动读，关闭它仍可用消息末尾的「朗读」按钮手动读。"
              + LF + "智谱 glm-tts 示例：地址 https://open.bigmodel.cn/api/paas/v4/audio/speech，模型 glm-tts，音色 tongtong，格式 wav。留空 API 地址则使用系统 TTS。"));
    }

    // ============================================================
    // 生成行为
    // ============================================================

    private void buildGenerate() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "gen_section_thinking", "思考与工具"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "gen_thinking", "思考深度"), thinkingLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "gen_pick_thinking", "思考深度"),
                            "thinking_effort", THINKING_IDS, thinkingLabels(), "");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "gen_max_rounds", "最大工具调用轮数"),
                prefs.getString("max_tool_rounds", "0"),
                new Runnable() { @Override public void run() {
                    editNumber(LanguageManager.t(SettingsDetailActivity.this, "gen_edit_max_rounds", "最大轮数（0 = 不限制）"),
                            "max_tool_rounds", "0");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "gen_retry", "自动重试次数"),
                prefs.getString("retry_times", "2"),
                new Runnable() { @Override public void run() {
                    editNumber(LanguageManager.t(SettingsDetailActivity.this, "gen_edit_retry", "重试次数"),
                            "retry_times", "2");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "gen_delete_sec", "删除文件确认秒数"),
                prefs.getString("delete_confirm_sec", "5"),
                new Runnable() { @Override public void run() {
                    editNumber(LanguageManager.t(SettingsDetailActivity.this, "gen_edit_delete_sec", "删除确认秒数（0 = 不确认）"),
                            "delete_confirm_sec", "5");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "gen_startup", "启动时打开"), startupLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "gen_pick_startup", "启动时打开"),
                            "startup_mode",
                            new String[]{"last", "new"},
                            new String[]{
                                LanguageManager.t(SettingsDetailActivity.this, "gen_startup_last", "上次对话"),
                                LanguageManager.t(SettingsDetailActivity.this, "gen_startup_new", "新对话")},
                            "last");
                }});

        addSectionTitle(LanguageManager.t(ctx, "gen_section_context", "上下文"));
        LinearLayout c2 = addCard();
        addSwitchRow(c2, LanguageManager.t(ctx, "gen_auto_compress", "自动压缩上下文"),
                prefs.getBoolean("auto_compress", true),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("auto_compress", checked).commit();
                    }
                });
        addValueRow(c2, LanguageManager.t(ctx, "gen_compress_rounds", "每多少轮压缩一次"),
                prefs.getString("compress_rounds", "10"),
                new Runnable() { @Override public void run() {
                    editNumber(LanguageManager.t(SettingsDetailActivity.this, "gen_edit_compress_rounds", "每多少轮压缩一次"),
                            "compress_rounds", "10");
                }});

        addHint(LanguageManager.t(ctx, "gen_hint",
                "思考深度选「None」会让支持关闭思考的模型不返回思考内容；最大轮数填 0 表示不限制。"));
    }

    private String thinkingLabel() {
        String cur = prefs.getString("thinking_effort", "");
        for (int i = 0; i < THINKING_IDS.length; i++) {
            if (THINKING_IDS[i].equals(cur)) return thinkingLabels()[i];
        }
        return thinkingLabels()[0];
    }

    private String startupLabel() {
        String cur = prefs.getString("startup_mode", "last");
        if ("new".equals(cur)) return LanguageManager.t(this, "gen_startup_new", "新对话");
        return LanguageManager.t(this, "gen_startup_last", "上次对话");
    }

    // ============================================================
    // 外观
    // ============================================================

    private void buildAppearance() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "appearance_section", "外观"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "appearance_theme", "主题风格"), themeLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "appearance_pick_theme", "主题风格（重启后完全生效）"),
                            "theme_style", THEME_VALUES, themeLabels(), "system");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "appearance_font", "字体大小"), fontLabel(),
                new Runnable() { @Override public void run() {
                    pickSingle(LanguageManager.t(SettingsDetailActivity.this, "appearance_pick_font", "字体大小"),
                            "font_scale", FONT_VALUES, fontLabels(), "0");
                }});
        addValueRow(c1, LanguageManager.t(ctx, "appearance_bg", "聊天背景图"),
                (prefs.getString("chat_bg_path", "") != null && prefs.getString("chat_bg_path", "").length() > 0)
                        ? LanguageManager.t(ctx, "appearance_bg_set", "已设置")
                        : LanguageManager.t(ctx, "appearance_bg_unset", "未设置"),
                new Runnable() { @Override public void run() { pickBackground(); } });
    }

    private String themeLabel() {
        String cur = prefs.getString("theme_style", "system");
        String[] labs = themeLabels();
        for (int i = 0; i < THEME_VALUES.length; i++) {
            if (THEME_VALUES[i].equals(cur)) return labs[i];
        }
        return labs[0];
    }

    private String fontLabel() {
        String cur = prefs.getString("font_scale", "0");
        String[] labs = fontLabels();
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (FONT_VALUES[i].equals(cur)) return labs[i];
        }
        return labs[0];
    }

    private void pickBackground() {
        Context ctx = this;
        final String cur = prefs.getString("chat_bg_path", "");
        String[] items;
        if (cur == null || cur.length() == 0) items = new String[]{LanguageManager.t(ctx, "appearance_from_gallery", "从相册选择图片")};
        else items = new String[]{
                LanguageManager.t(ctx, "appearance_from_gallery", "从相册选择图片"),
                LanguageManager.t(ctx, "appearance_clear_bg", "清除背景图")};
        new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(ctx, "appearance_pick_bg", "聊天背景图"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) {
                            try {
                                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                                it.setType("image/*");
                                it.addCategory(Intent.CATEGORY_OPENABLE);
                                startActivityForResult(Intent.createChooser(it,
                                        LanguageManager.t(SettingsDetailActivity.this, "appearance_choose_bg", "选择聊天背景图")), 8001);
                            } catch (Throwable t) {
                                Toast.makeText(SettingsDetailActivity.this,
                                        LanguageManager.t(SettingsDetailActivity.this, "appearance_no_gallery", "无法打开相册"),
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            prefs.edit().putString("chat_bg_path", "").commit();
                            sendBroadcast(new Intent("com.ai.office.REFRESH_BG"));
                            Toast.makeText(SettingsDetailActivity.this,
                                    LanguageManager.t(SettingsDetailActivity.this, "appearance_bg_cleared", "已清除"),
                                    Toast.LENGTH_SHORT).show();
                            recreate();
                        }
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
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
                Toast.makeText(this, LanguageManager.t(this, "appearance_bg_set_ok", "已设置背景图"), Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent("com.ai.office.REFRESH_BG"));
                recreate();
            } catch (Throwable t) {
                Toast.makeText(this, LanguageManager.t(this, "appearance_bg_fail", "背景图保存失败: ") + t.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQ_IMPORT_LANG) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                String result = LanguageManager.importFromUri(this, data.getData());
                if (result != null && result.startsWith("错误")) {
                    Toast.makeText(this,
                            LanguageManager.t(this, "lang_import_fail", "导入失败：") + result,
                            Toast.LENGTH_LONG).show();
                } else {
                    LanguageManager.setCurrentId(this, result);
                    Toast.makeText(this,
                            LanguageManager.t(this, "lang_import_done", "语言包已导入：") + result,
                            Toast.LENGTH_LONG).show();
                    recreate();
                }
            }
            return;
        }
    }

    // ============================================================
    // 功能开关
    // ============================================================

    private void buildToggle() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "toggle_section_behavior", "行为开关"));
        LinearLayout c1 = addCard();

        addSwitchRow(c1, LanguageManager.t(ctx, "toggle_notify", "后台完成提醒"),
                prefs.getBoolean("notify_done", true),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("notify_done", checked).commit();
                    }
                });
        addSwitchRow(c1, LanguageManager.t(ctx, "toggle_image_as_file", "图片保存为本地路径发送"),
                prefs.getBoolean("image_as_file", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("image_as_file", checked).commit();
                    }
                });

        addSectionTitle(LanguageManager.t(ctx, "toggle_section_advanced", "高级权限"));
        LinearLayout c2 = addCard();

        addSwitchRow(c2, LanguageManager.t(ctx, "toggle_script", "允许 script 类型插件"),
                prefs.getBoolean("allow_script_plugins", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_script_plugins", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? LanguageManager.t(SettingsDetailActivity.this, "toggle_script_on", "已开启 script 插件，重启 App 后生效")
                                        : LanguageManager.t(SettingsDetailActivity.this, "toggle_script_off", "已关闭 script 插件"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
        addSwitchRow(c2, LanguageManager.t(ctx, "toggle_shell", "允许 AI 执行命令（shell / JS）"),
                prefs.getBoolean("allow_shell_tool", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_shell_tool", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? LanguageManager.t(SettingsDetailActivity.this, "toggle_shell_on", "已开启，返回主界面即生效")
                                        : LanguageManager.t(SettingsDetailActivity.this, "toggle_shell_off", "已关闭命令执行工具"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
        addSwitchRow(c2, LanguageManager.t(ctx, "toggle_a11y", "允许 AI 操控手机（无障碍）"),
                prefs.getBoolean("allow_accessibility_tool", false),
                new SwitchListener() {
                    @Override public void onChanged(boolean checked) {
                        prefs.edit().putBoolean("allow_accessibility_tool", checked).commit();
                        Toast.makeText(SettingsDetailActivity.this,
                                checked ? LanguageManager.t(SettingsDetailActivity.this, "toggle_a11y_on", "已开启。请到「权限」里开启无障碍服务")
                                        : LanguageManager.t(SettingsDetailActivity.this, "toggle_a11y_off", "已关闭手机操控工具"),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        addHint(LanguageManager.t(ctx, "toggle_hint", "script 插件能执行 shell 命令，只在你信任插件来源时开启。"));
    }

    // ============================================================
    // 长期记忆
    // ============================================================

    private void buildMemory() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "memory_section", "长期记忆"));
        LinearLayout c1 = addCard();
        int count = MemoryStore.getAllKeys(this).size();
        addValueRow(c1, LanguageManager.t(ctx, "memory_manage", "管理记忆条目"), count + "",
                new Runnable() { @Override public void run() { showMemoryManager(); } });
        addHint(LanguageManager.t(ctx, "memory_hint", "AI 每次对话都会参考这些记忆；可在对话框里手动添加，也可点条目删除。"));
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
        items.add(LanguageManager.t(this, "memory_add_new", "＋ 新增记忆"));

        new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(this, "memory_picker_title", "长期记忆（共 %d 条）").replace("%d", String.valueOf(entries.size())))
                .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == items.size() - 1) addOrEditMemory(null);
                        else showMemoryDetail(entries.get(which)[0], entries.get(which)[1]);
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_close", "关闭"), null)
                .show();
    }

    private void showMemoryDetail(final String key, String content) {
        final String[] ops = new String[]{
                LanguageManager.t(this, "memory_edit", "编辑"),
                LanguageManager.t(this, "memory_delete", "删除")};
        new GlassDialog.Builder(this)
                .setTitle(key)
                .setMessage(content.length() > 2000 ? content.substring(0, 2000) + "…" : content)
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) addOrEditMemory(key);
                        else {
                            new GlassDialog.Builder(SettingsDetailActivity.this)
                                    .setTitle(LanguageManager.t(SettingsDetailActivity.this, "memory_delete_confirm", "删除记忆 %s？").replace("%s", key))
                                    .setPositiveButton(LanguageManager.t(SettingsDetailActivity.this, "common_delete", "删除"), new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dd, int w) {
                                            MemoryStore.delete(SettingsDetailActivity.this, key);
                                            dd.dismiss();
                                            Toast.makeText(SettingsDetailActivity.this,
                                                    LanguageManager.t(SettingsDetailActivity.this, "memory_deleted", "已删除"),
                                                    Toast.LENGTH_SHORT).show();
                                            recreate();
                                        }
                                    })
                                    .setNegativeButton(LanguageManager.t(SettingsDetailActivity.this, "common_cancel", "取消"), null)
                                    .show();
                        }
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_close", "关闭"), null)
                .show();
    }

private void addOrEditMemory(final String oldKey) {
    final EditText etKey = new EditText(this);
    etKey.setHint(LanguageManager.t(this, "memory_key_hint", "记忆标识（英文/数字，如 user_pref）"));
    etKey.setTextSize(15);
    etKey.setSingleLine(true);
    etKey.setIncludeFontPadding(false);
    etKey.setGravity(Gravity.CENTER_VERTICAL);
    etKey.setInputType(InputType.TYPE_CLASS_TEXT);
    etKey.setPadding(UiUtils.dp(this, 14), 0, UiUtils.dp(this, 14), 0);
    etKey.setBackgroundDrawable(UiOverrides.editBgDrawable(this));
    etKey.setMinHeight(UiUtils.dp(this, 46));
    if (oldKey != null) etKey.setText(oldKey);

    final EditText etVal = new EditText(this);
    etVal.setHint(LanguageManager.t(this, "memory_value_hint", "记忆内容"));
    etVal.setTextSize(15);
    etVal.setIncludeFontPadding(false);
    etVal.setGravity(Gravity.TOP | Gravity.LEFT);
    etVal.setMinLines(3);
    etVal.setMaxLines(10);
    etVal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    etVal.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 10),
                     UiUtils.dp(this, 14), UiUtils.dp(this, 10));
    etVal.setBackgroundDrawable(UiOverrides.editBgDrawable(this));
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
    LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    wrap.addView(etKey, kp);
    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    vp.topMargin = UiUtils.dp(this, 12);
    wrap.addView(etVal, vp);

    new GlassDialog.Builder(this)
            .setTitle(oldKey == null
                    ? LanguageManager.t(this, "memory_add_title", "新增记忆")
                    : LanguageManager.t(this, "memory_edit_title", "编辑记忆"))
            .setView(wrap)
            .setPositiveButton(LanguageManager.t(this, "common_save", "保存"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String k = etKey.getText().toString().trim();
                    String v = etVal.getText().toString().trim();
                    if (k.length() == 0) {
                        Toast.makeText(SettingsDetailActivity.this,
                                LanguageManager.t(SettingsDetailActivity.this, "memory_key_required", "标识不能为空"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (oldKey != null && !oldKey.equals(k)) {
                        MemoryStore.delete(SettingsDetailActivity.this, oldKey);
                    }
                    MemoryStore.save(SettingsDetailActivity.this, k, v);
                    d.dismiss();
                    Toast.makeText(SettingsDetailActivity.this,
                            LanguageManager.t(SettingsDetailActivity.this, "memory_saved", "已保存"),
                            Toast.LENGTH_SHORT).show();
                    recreate();
                }
            })
            .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
            .show();
}

    // ============================================================
    // 系统提示词
    // ============================================================

    private void buildPrompt() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "prompt_section", "系统提示词"));
        LinearLayout c1 = addCard();
        String sp = prefs.getString("system_prompt", "");
        addValueRow(c1, LanguageManager.t(ctx, "prompt_edit", "编辑系统提示词"),
                (sp == null || sp.length() == 0)
                        ? LanguageManager.t(ctx, "prompt_default", "默认")
                        : LanguageManager.t(ctx, "prompt_custom", "已自定义"),
                new Runnable() { @Override public void run() { editSystemPrompt(); } });
        addHint(LanguageManager.t(ctx, "prompt_hint", "留空则使用内置默认提示词。"));
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

        new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(this, "prompt_dialog_title", "系统提示词（留空使用默认）"))
                .setView(et)
                .setPositiveButton(LanguageManager.t(this, "common_save", "保存"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String v = et.getText().toString().trim();
                        prefs.edit().putString("system_prompt", v).commit();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
    }

    // ============================================================
    // 插件（含 UI 覆盖管理）
    // ============================================================

    private void buildPlugin() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "plugin_section_loaded", "已加载的插件"));
        LinearLayout c1 = addCard();
        addValueRow(c1, LanguageManager.t(ctx, "plugin_view", "查看已加载的插件"), "",
                new Runnable() { @Override public void run() { showPlugins(); } });

        List<JSONObject> uiPlugins = PluginManager.getUiPluginsByExtension(this, PluginManager.EXT_SETTINGS_ITEM);
        if (!uiPlugins.isEmpty()) {
            addSectionTitle(LanguageManager.t(ctx, "plugin_section_ui", "UI 插件动作"));
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

        addSectionTitle(LanguageManager.t(ctx, "plugin_section_override", "UI 覆盖（插件配色）"));
        LinearLayout c3 = addCard();
        int cnt = UiOverrides.countOverrides(this);
        addValueRow(c3, LanguageManager.t(ctx, "plugin_view_override", "查看当前 UI 覆盖"),
                cnt == 0 ? LanguageManager.t(ctx, "plugin_override_none", "（无）")
                         : LanguageManager.t(ctx, "plugin_override_count", "%d 项").replace("%d", String.valueOf(cnt)),
                new Runnable() { @Override public void run() { showUiOverrides(); } });
        addValueRow(c3, LanguageManager.t(ctx, "plugin_clear_override", "清除所有 UI 覆盖"), "",
                new Runnable() { @Override public void run() { confirmClearUiOverrides(); } });

        addHint(LanguageManager.t(ctx, "plugin_hint",
                "把 .json 插件放在 /sdcard/AI/plugins/ 下，重启 App 即生效。extension 为 settings_item 的 UI 插件会出现在上方。"
              + LF + "action.kind = ui_change 的插件可以覆盖 App 配色，见 bubble_theme.json 示例。"));
    }

    private void showUiOverrides() {
        try {
            String list = UiOverrides.listOverrides(this);
            new GlassDialog.Builder(this)
                    .setTitle(LanguageManager.t(this, "ui_override_list_title", "当前 UI 覆盖"))
                    .setMessage(list)
                    .setPositiveButton(LanguageManager.t(this, "common_close", "关闭"), null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, LanguageManager.t(this, "plugin_read_fail", "读取失败: ") + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearUiOverrides() {
        new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(this, "plugin_clear_confirm_title", "清除所有 UI 覆盖？"))
                .setMessage(LanguageManager.t(this, "plugin_clear_confirm_msg",
                        "将把 App 的颜色 / 尺寸恢复为默认值（不影响其它设置）。"
                      + LF + LF + "提示：清除后回到主界面即可看到效果。"))
                .setPositiveButton(LanguageManager.t(this, "chat_clear", "清除"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        UiOverrides.clearAll(SettingsDetailActivity.this);
                        d.dismiss();
                        Toast.makeText(SettingsDetailActivity.this,
                                LanguageManager.t(SettingsDetailActivity.this, "plugin_cleared", "已清除，返回主界面生效"),
                                Toast.LENGTH_LONG).show();
                        recreate();
                    }
                })
                .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                .show();
    }

    private void showPlugins() {
        try {
            Context ctx = this;
            List<JSONObject> aiPlugins = PluginManager.loadPlugins(this);
            List<JSONObject> uiPlugins = PluginManager.loadUiPlugins(this);
            StringBuilder sb = new StringBuilder();
            sb.append(LanguageManager.t(ctx, "plugin_count_ai", "AI 工具插件：%d 个").replace("%d", String.valueOf(aiPlugins.size()))).append(LF);
            sb.append(LanguageManager.t(ctx, "plugin_count_ui", "UI 扩展插件：%d 个").replace("%d", String.valueOf(uiPlugins.size()))).append(LF).append(LF);

            if (!aiPlugins.isEmpty()) {
                sb.append(LanguageManager.t(ctx, "plugin_section_ai_header", "── AI 工具插件 ──")).append(LF);
                for (int i = 0; i < aiPlugins.size(); i++) {
                    JSONObject p = aiPlugins.get(i);
                    sb.append("• ").append(p.optString("name", ""))
                      .append(" [").append(p.optString("_type", "prompt")).append("]").append(LF);
                    String d = p.optString("description", "");
                    if (d.length() > 0) sb.append("  ").append(d).append(LF);
                }
                sb.append(LF);
            }
            if (!uiPlugins.isEmpty()) {
                sb.append(LanguageManager.t(ctx, "plugin_section_ui_header", "── UI 扩展插件 ──")).append(LF);
                for (int i = 0; i < uiPlugins.size(); i++) {
                    JSONObject p = uiPlugins.get(i);
                    sb.append("• ").append(p.optString("title", p.optString("name", "")))
                      .append(" → ").append(p.optString("extension", "")).append(LF);
                    String d = p.optString("description", "");
                    if (d.length() > 0) sb.append("  ").append(d).append(LF);
                }
                sb.append(LF);
            }

            sb.append(LanguageManager.t(ctx, "plugin_dir", "插件目录: /sdcard/AI/plugins/")).append(LF);
            sb.append(LanguageManager.t(ctx, "plugin_ext_list", "extension 可选：main_menu / message_long_press / input_plus / settings_item / toolbar")).append(LF);
            sb.append(LanguageManager.t(ctx, "plugin_kind_list", "action.kind 可选：prompt / http / script / ui_change"));

            new GlassDialog.Builder(this)
                    .setTitle(LanguageManager.t(this, "more_menu_view_loaded_plugins", "查看已加载的插件"))
                    .setMessage(sb.toString())
                    .setPositiveButton(LanguageManager.t(this, "common_know", "知道了"), null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, LanguageManager.t(this, "plugin_read_fail", "读取失败: ") + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runSettingsPlugin(final JSONObject plugin, final JSONObject args) {
        try {
            final JSONObject action = plugin.optJSONObject("action");
            final String kind = action == null ? "prompt" : action.optString("kind", "prompt").toLowerCase();

            if ("script".equals(kind)) {
                new GlassDialog.Builder(this)
                        .setTitle(LanguageManager.t(this, "plugin_exec_script_title", "执行脚本？"))
                        .setMessage(LanguageManager.t(this, "plugin_exec_script_msg", "该插件将执行本机 shell 脚本。只在你信任插件来源时继续。"))
                        .setPositiveButton(LanguageManager.t(this, "plugin_exec", "执行"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                d.dismiss();
                                doRunSettingsPlugin(plugin, args);
                            }
                        })
                        .setNegativeButton(LanguageManager.t(this, "common_cancel", "取消"), null)
                        .show();
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
                        new GlassDialog.Builder(SettingsDetailActivity.this)
                                .setTitle(LanguageManager.t(SettingsDetailActivity.this, "plugin_result_title", "插件结果"))
                                .setMessage(result == null ? "（空）" : result)
                                .setPositiveButton(LanguageManager.t(SettingsDetailActivity.this, "common_copy", "复制"), new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        MarkdownView.copyText(SettingsDetailActivity.this, result);
                                        d.dismiss();
                                    }
                                })
                                .setNegativeButton(LanguageManager.t(SettingsDetailActivity.this, "common_close", "关闭"), null)
                                .show();
                    }
                });
            }
        }).start();
    }

    // ============================================================
    // 权限
    // ============================================================

    private void buildPermission() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "permission_section", "系统权限"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "permission_all_files", "申请「所有文件访问权限」"), "", new Runnable() {
            @Override public void run() { openAllFilesSettings(); }
        });
        addValueRow(c1, LanguageManager.t(ctx, "permission_a11y", "去开启「无障碍服务」"), "", new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Throwable t) {
                    Toast.makeText(SettingsDetailActivity.this,
                            LanguageManager.t(SettingsDetailActivity.this, "permission_no_a11y", "无法打开无障碍设置"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        addHint(LanguageManager.t(ctx, "permission_hint",
                "Android 11 及以上需要「所有文件访问权限」才能读写 /sdcard；AI 操控手机需要无障碍服务。"));
    }

    private void openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, LanguageManager.t(this, "permission_not_needed", "当前系统版本无需此权限"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent it = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            it.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(it); }
            catch (Throwable t) { startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")); }
        } catch (Throwable t) {
            Toast.makeText(this, LanguageManager.t(this, "permission_no_all_files", "无法打开权限设置页"), Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // 余额查询
    // ============================================================

    private void buildBalance() {
        Context ctx = this;
        addSectionTitle(LanguageManager.t(ctx, "balance_section", "余额查询"));
        LinearLayout c1 = addCard();

        addValueRow(c1, LanguageManager.t(ctx, "balance_url", "余额查询接口"),
                shortText(prefs.getString("balance_url", ""), LanguageManager.t(ctx, "ai_unset", "（未设置）")),
                new Runnable() { @Override public void run() {
                    editText(LanguageManager.t(SettingsDetailActivity.this, "balance_edit_url", "余额查询接口 URL"),
                            "balance_url", "", false);
                }});
        addValueRow(c1, LanguageManager.t(ctx, "balance_query", "立即查询"), "",
                new Runnable() { @Override public void run() { queryBalance(); } });

        addHint(LanguageManager.t(ctx, "balance_hint", "一般用各供应商提供的余额接口，例如 DeepSeek 的 /user/balance。"));
    }

    private void queryBalance() {
        final String url = prefs.getString("balance_url", "").trim();
        final String key = prefs.getString("api_key", "").trim();
        if (url.length() == 0) {
            Toast.makeText(this, LanguageManager.t(this, "balance_url_required", "请先设置「余额查询接口」"), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, LanguageManager.t(this, "balance_querying", "正在查询…"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String result = doQueryBalance(url, key);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new GlassDialog.Builder(SettingsDetailActivity.this)
                                .setTitle(LanguageManager.t(SettingsDetailActivity.this, "balance_result", "余额查询结果"))
                                .setMessage(result)
                                .setPositiveButton(LanguageManager.t(SettingsDetailActivity.this, "common_know", "知道了"), null)
                                .show();
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
            if (code != 200) return "HTTP " + code + LF + clip(sb.toString(), 400);
            return clip(sb.toString(), 1500);
        } catch (Throwable t) {
            return LanguageManager.t(this, "balance_fail", "查询失败: ") + t.getMessage();
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + LF + "...(已截断)";
    }

    // ============================================================
    // 语言
    // ============================================================

    private void buildLanguage() {
        final Context ctx = this;

        addSectionTitle(LanguageManager.t(ctx, "lang_builtin", "内置语言"));
        LinearLayout c1 = addCard();
        final String curId = LanguageManager.getCurrentId(this);
        final List<String[]> builtin = LanguageManager.listBuiltin();
        for (int i = 0; i < builtin.size(); i++) {
            final String id = builtin.get(i)[0];
            final String name = builtin.get(i)[1];
            boolean checked = id.equals(curId);
            addValueRow(c1, name + (checked ? "  ✓" : ""), "", new Runnable() {
                @Override public void run() {
                    LanguageManager.setCurrentId(ctx, id);
                    Toast.makeText(SettingsDetailActivity.this,
                            LanguageManager.t(ctx, "lang_switched", "已切换语言"),
                            Toast.LENGTH_SHORT).show();
                    recreate();
                }
            });
        }

        addSectionTitle(LanguageManager.t(ctx, "lang_custom", "自定义语言包"));
        LinearLayout c2 = addCard();

        final List<String[]> custom = LanguageManager.listCustom(this);
        if (custom.isEmpty()) {
            addValueRow(c2, LanguageManager.t(ctx, "lang_none_custom", "（暂无自定义语言包）"), "",
                    new Runnable() { @Override public void run() {} });
        } else {
            for (int i = 0; i < custom.size(); i++) {
                final String id = custom.get(i)[0];
                final String name = custom.get(i)[1];
                boolean checked = id.equals(curId);
                addValueRow(c2, name + (checked ? "  ✓" : ""), "", new Runnable() {
                    @Override public void run() {
                        LanguageManager.setCurrentId(ctx, id);
                        Toast.makeText(SettingsDetailActivity.this,
                                LanguageManager.t(ctx, "lang_switched", "已切换语言"),
                                Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                });
            }
        }

        addValueRow(c2, LanguageManager.t(ctx, "lang_import", "导入语言包（.json）"), "",
                new Runnable() {
                    @Override public void run() {
                        try {
                            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                            it.setType("*/*");
                            it.addCategory(Intent.CATEGORY_OPENABLE);
                            it.putExtra(Intent.EXTRA_MIME_TYPES,
                                    new String[]{"application/json", "text/plain", "*/*"});
                            startActivityForResult(Intent.createChooser(it,
                                    LanguageManager.t(SettingsDetailActivity.this, "lang_choose_file", "选择语言包")),
                                    REQ_IMPORT_LANG);
                        } catch (Throwable t) {
                            Toast.makeText(SettingsDetailActivity.this,
                                    LanguageManager.t(SettingsDetailActivity.this, "lang_no_picker", "无法打开文件选择器: ") + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        addValueRow(c2, LanguageManager.t(ctx, "lang_export", "导出当前语言包"), "",
                new Runnable() { @Override public void run() { exportCurrentLang(); } });

        if (!custom.isEmpty()) {
            addValueRow(c2, LanguageManager.t(ctx, "lang_delete", "删除语言包"), "",
                    new Runnable() {
                        @Override public void run() { showDeleteLangMenu(custom); }
                    });
        }

        addHint(LanguageManager.t(ctx, "lang_hint",
                "语言包格式：JSON，需包含 name 与 strings 字段。可从内置语言包导出作为模板，翻译后重新导入。"
              + "切换语言后设置页立即生效，其他页面会在下次进入时生效。"));
    }

    private void showDeleteLangMenu(final List<String[]> custom) {
        if (custom == null || custom.isEmpty()) return;
        final String[] names = new String[custom.size()];
        final String[] ids = new String[custom.size()];
        for (int i = 0; i < custom.size(); i++) {
            names[i] = custom.get(i)[1];
            ids[i] = custom.get(i)[0];
        }
        GlassMenuDialog.showItems(this,
                LanguageManager.t(this, "lang_delete", "删除语言包"),
                names,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        final String did = ids[which];
                        final String dname = names[which];
                        new GlassDialog.Builder(SettingsDetailActivity.this)
                                .setTitle(LanguageManager.t(SettingsDetailActivity.this, "lang_delete_title", "删除语言包 %s？").replace("%s", dname))
                                .setPositiveButton(LanguageManager.t(SettingsDetailActivity.this, "common_delete", "删除"), new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dd, int w) {
                                        LanguageManager.deleteCustom(SettingsDetailActivity.this, did);
                                        dd.dismiss();
                                        recreate();
                                    }
                                })
                                .setNegativeButton(LanguageManager.t(SettingsDetailActivity.this, "common_cancel", "取消"), null)
                                .show();
                    }
                });
    }

    private void exportCurrentLang() {
        final String path = LanguageManager.exportCurrent(this);
        if (path == null || path.startsWith("错误")) {
            Toast.makeText(this,
                    LanguageManager.t(this, "lang_import_fail", "导出失败：") + path,
                    Toast.LENGTH_LONG).show();
            return;
        }
        new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(this, "lang_export", "导出当前语言包"))
                .setMessage(LanguageManager.t(this, "lang_export_done", "已导出到：") + LF + path)
                .setPositiveButton(LanguageManager.t(this, "common_share", "分享"),
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    Intent it = new Intent(Intent.ACTION_SEND);
                                    it.setType("application/json");
                                    it.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new java.io.File(path)));
                                    startActivity(Intent.createChooser(it,
                                            LanguageManager.t(SettingsDetailActivity.this, "lang_share", "分享语言包")));
                                } catch (Throwable t) {}
                                d.dismiss();
                            }
                        })
                .setNegativeButton(LanguageManager.t(this, "common_close", "关闭"), null)
                .show();
    }

    // ============================================================
    // 实验性功能
    // ============================================================

    private void buildExperimental() {
        final Context ctx = this;

        addSectionTitle(LanguageManager.t(ctx, "experimental_section", "实验性功能"));

        LinearLayout c1 = addCard();
        final boolean enabled = UiOverrides.glassEnabled(this);

        addSwitchRow(c1, LanguageManager.t(ctx, "experimental_glass_enabled", "毛玻璃效果"), enabled, new SwitchListener() {
            @Override public void onChanged(boolean checked) {
                if (checked) {
                    confirmEnableGlass();
                } else {
                    UiUtils.prefs(SettingsDetailActivity.this).edit().remove("ui_glass_enabled").commit();
                    Toast.makeText(SettingsDetailActivity.this,
                            LanguageManager.t(ctx, "experimental_disabled_toast", "已关闭毛玻璃效果"),
                            Toast.LENGTH_SHORT).show();
                    recreate();
                }
            }
        });

        if (enabled) {
            final String[] alphaLabels = new String[]{"80%", "70%", "60%", "50%"};
            final String[] alphaValues = new String[]{"204", "178", "153", "128"};
            int curAlpha = UiOverrides.glassAlphaValue(this);
            String curLabel = "80%";
            for (int i = 0; i < alphaValues.length; i++) {
                if (String.valueOf(curAlpha).equals(alphaValues[i])) { curLabel = alphaLabels[i]; break; }
            }
            addValueRow(c1, LanguageManager.t(ctx, "experimental_glass_alpha", "气泡不透明度"),
                    curLabel, new Runnable() {
                @Override public void run() {
                    pickSingle(LanguageManager.t(ctx, "experimental_glass_alpha", "气泡不透明度"),
                            "ui_glass_alpha", alphaValues, alphaLabels, "204");
                }
            });
        }

        addHint(LanguageManager.t(ctx, "experimental_glass_hint",
                "毛玻璃效果会对聊天背景做一次高斯模糊，气泡、卡片、工具面板、"
              + "历史对话项、顶栏、输入栏、抽屉等 UI 元素变为半透明玻璃质感，"
              + "视觉上接近 iOS 的 Liquid Glass。"
              + LF + LF
              + "这是实验性功能，可能需要约 1-2 秒处理背景图；"
              + "低端机型或超大背景图可能出现短暂卡顿。请谨慎开启。"));
    }

    private void confirmEnableGlass() {
        final Context ctx = this;
        final int waitSec = 5;
        final Dialog[] holder = new Dialog[1];
        final android.os.CountDownTimer[] timer = new android.os.CountDownTimer[1];
        final TextView[] okBtn = new TextView[1];

        holder[0] = new GlassDialog.Builder(this)
                .setTitle(LanguageManager.t(ctx, "experimental_confirm_title", "实验性功能确认"))
                .setMessage(LanguageManager.t(ctx, "experimental_confirm_msg",
                        "这是实验性功能，如果您需要开启，请您先确定您在做什么。"
                      + LF + LF
                      + "开启后所有 UI 元素将呈现玻璃质感，可能需要 1-2 秒处理背景；"
                      + "低端机型可能出现卡顿。"
                      + LF + LF
                      + "请等待 5 秒后确认。"))
                .setPositiveButton(LanguageManager.t(ctx, "experimental_confirm_ok_wait", "我确定（%d）").replace("%d", String.valueOf(waitSec)), null)
                .setNegativeButton(LanguageManager.t(ctx, "common_cancel", "取消"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        recreate();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface d) {
                        try { if (timer[0] != null) timer[0].cancel(); } catch (Throwable t) {}
                    }
                })
                .show();

        okBtn[0] = GlassDialog.Builder.getButton(holder[0], DialogInterface.BUTTON_POSITIVE);
        if (okBtn[0] != null) {
            okBtn[0].setEnabled(false);
            okBtn[0].setTextColor(0x60000000);
            okBtn[0].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    UiUtils.prefs(SettingsDetailActivity.this).edit()
                            .putString("ui_glass_enabled", "1").commit();
                    try { holder[0].dismiss(); } catch (Throwable t) {}
                    Toast.makeText(SettingsDetailActivity.this,
                            LanguageManager.t(ctx, "experimental_enabled_toast", "已开启毛玻璃效果"),
                            Toast.LENGTH_SHORT).show();
                    recreate();
                }
            });
        }

        final String okTpl = LanguageManager.t(ctx, "experimental_confirm_ok_wait", "我确定（%d）");
        final String okDone = LanguageManager.t(ctx, "experimental_confirm_ok", "我确定");

        timer[0] = new android.os.CountDownTimer(waitSec * 1000L, 1000L) {
            @Override public void onTick(long ms) {
                int remain = (int) ((ms + 999) / 1000);
                try {
                    if (okBtn[0] != null) okBtn[0].setText(okTpl.replace("%d", String.valueOf(remain)));
                } catch (Throwable t) {}
            }
            @Override public void onFinish() {
                try {
                    if (okBtn[0] != null) {
                        okBtn[0].setEnabled(true);
                        okBtn[0].setTextColor(UiOverrides.primary(SettingsDetailActivity.this));
                        okBtn[0].setText(okDone);
                    }
                } catch (Throwable t) {}
            }
        };
        try { timer[0].start(); } catch (Throwable t) {}
    }
}