package com.ai.office;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 设置首页：分类卡片列表。
 * 每一行点击后跳转到 SettingsDetailActivity，通过 EXTRA_CATEGORY 指定分类。
 * 从二级页返回时 onResume 会刷新每行的状态值。
 */
public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_settings);

            TextView btnBack = (TextView) findViewById(R.id.btnBack);
            if (btnBack != null) {
                btnBack.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { finish(); }
                });
            }

            // 设置页顶栏标题与分组标题
            TextView tvTitle = findViewById(android.R.id.content) != null ? null : null;
            applyStaticTexts();

            setupRow(R.id.rowAi, "ai");
            setupRow(R.id.rowVision, "vision");
            setupRow(R.id.rowSearch, "search");
            setupRow(R.id.rowTts, "tts");
            setupRow(R.id.rowRealtime, "realtime");
            setupRow(R.id.rowGenerate, "generate");
            setupRow(R.id.rowAppearance, "appearance");
            setupRow(R.id.rowLanguage, "language");
            setupRow(R.id.rowToggle, "toggle");
            setupRow(R.id.rowMemory, "memory");
            setupRow(R.id.rowPrompt, "prompt");
            setupRow(R.id.rowPlugin, "plugin");
            setupRow(R.id.rowPermission, "permission");
            setupRow(R.id.rowBalance, "balance");
        } catch (Throwable t) {
            Toast.makeText(this, "Settings load failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** 应用静态文案（标题 / 分组 / 行标题），支持语言包 */
    private void applyStaticTexts() {
        try {
            final Context ctx = this;
            // 顶部标题
            TextView title = findTitleView();
            if (title != null) title.setText(LanguageManager.t(ctx, "settings", "设置"));

            // 分组标题
            setTextById(findViewByText("AI 与模型"), LanguageManager.t(ctx, "settings_section_ai", "AI 与模型"));
            setTextById(findViewByText("语音"), LanguageManager.t(ctx, "settings_section_voice", "语音"));
            setTextById(findViewByText("应用"), LanguageManager.t(ctx, "settings_section_app", "应用"));
            setTextById(findViewByText("数据"), LanguageManager.t(ctx, "settings_section_data", "数据"));
            setTextById(findViewByText("系统"), LanguageManager.t(ctx, "settings_section_system", "系统"));

            // 行标题
            setTextById(findViewInRow(R.id.rowAi), LanguageManager.t(ctx, "settings_row_ai", "AI 接入"));
            setTextById(findViewInRow(R.id.rowVision), LanguageManager.t(ctx, "settings_row_vision", "识图 API"));
            setTextById(findViewInRow(R.id.rowSearch), LanguageManager.t(ctx, "settings_row_search", "联网搜索"));
            setTextById(findViewInRow(R.id.rowTts), LanguageManager.t(ctx, "settings_row_tts", "语音合成 TTS"));
            setTextById(findViewInRow(R.id.rowRealtime), LanguageManager.t(ctx, "settings_row_realtime", "实时通话"));
            setTextById(findViewInRow(R.id.rowGenerate), LanguageManager.t(ctx, "settings_row_generate", "生成行为"));
            setTextById(findViewInRow(R.id.rowAppearance), LanguageManager.t(ctx, "settings_row_appearance", "外观"));
            setTextById(findViewInRow(R.id.rowLanguage), LanguageManager.t(ctx, "settings_row_language", "语言"));
            setTextById(findViewInRow(R.id.rowToggle), LanguageManager.t(ctx, "settings_row_toggle", "功能开关"));
            setTextById(findViewInRow(R.id.rowMemory), LanguageManager.t(ctx, "settings_row_memory", "长期记忆"));
            setTextById(findViewInRow(R.id.rowPrompt), LanguageManager.t(ctx, "settings_row_prompt", "系统提示词"));
            setTextById(findViewInRow(R.id.rowPlugin), LanguageManager.t(ctx, "settings_row_plugin", "插件"));
            setTextById(findViewInRow(R.id.rowPermission), LanguageManager.t(ctx, "settings_row_permission", "权限"));
            setTextById(findViewInRow(R.id.rowBalance), LanguageManager.t(ctx, "settings_row_balance", "余额查询"));
        } catch (Throwable t) {}
    }

    private TextView findTitleView() {
        try {
            // 标题是在 activity_settings.xml 中居中的那个 TextView，无 id
            android.view.ViewGroup root = (android.view.ViewGroup) findViewById(android.R.id.content);
            if (root == null) return null;
            android.view.ViewGroup outer = (android.view.ViewGroup) root.getChildAt(0);
            if (outer == null) return null;
            android.view.ViewGroup frame = (android.view.ViewGroup) outer.getChildAt(0);
            if (frame == null) return null;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View v = frame.getChildAt(i);
                if (v instanceof TextView && v.getId() == View.NO_ID) return (TextView) v;
            }
        } catch (Throwable t) {}
        return null;
    }

    private View findViewByText(String text) {
        try {
            android.view.ViewGroup root = (android.view.ViewGroup) findViewById(android.R.id.content);
            return findTextRecursive(root, text);
        } catch (Throwable t) { return null; }
    }

    private View findTextRecursive(android.view.ViewGroup vg, String text) {
        if (vg == null) return null;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View v = vg.getChildAt(i);
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && text.equals(cs.toString())) return v;
            }
            if (v instanceof android.view.ViewGroup) {
                View r = findTextRecursive((android.view.ViewGroup) v, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private View findViewInRow(int rowId) {
        try {
            View row = findViewById(rowId);
            if (!(row instanceof android.view.ViewGroup)) return null;
            android.view.ViewGroup vg = (android.view.ViewGroup) row;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View v = vg.getChildAt(i);
                if (v instanceof TextView && v.getId() == View.NO_ID) return v;
            }
        } catch (Throwable t) {}
        return null;
    }

    private void setTextById(View v, String text) {
        try {
            if (v instanceof TextView) ((TextView) v).setText(text);
        } catch (Throwable t) {}
    }

    private void setupRow(int rowId, final String category) {
        View row = findViewById(rowId);
        if (row == null) return;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent it = new Intent(SettingsActivity.this, SettingsDetailActivity.class);
                    it.putExtra(SettingsDetailActivity.EXTRA_CATEGORY, category);
                    startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(SettingsActivity.this,
                            LanguageManager.t(SettingsActivity.this, "err_no_open_settings", "无法打开设置: ") + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyStaticTexts();
        refreshAllStates();
    }

    private void refreshAllStates() {
        try {
            final Context ctx = this;
            setText(R.id.tvAiState, shortText(UiUtils.getStr(this, "model", "deepseek-flash"), ""));
            setText(R.id.tvVisionState, shortText(UiUtils.getStr(this, "vision_model", ""),
                    LanguageManager.t(ctx, "ai_unset", "未设置")));
            setText(R.id.tvSearchState, SearchProvider.nameOf(UiUtils.getStr(this, "search_provider", "bing")));

            boolean ttsOn = UiUtils.getBool(this, "tts_enabled", false);
            setText(R.id.tvTtsState, ttsOn ? "已开启" : "已关闭");

            String rtProvider = UiUtils.getStr(this, "realtime_provider", "glm-realtime");
            setText(R.id.tvRealtimeState, RealtimeConfig.nameOf(rtProvider));

            setText(R.id.tvGenerateState, thinkingShortLabel());
            setText(R.id.tvAppearanceState, themeShortLabel());
            setText(R.id.tvLanguageState, languageStateLabel());

            try {
                int n = MemoryStore.getAllKeys(this).size();
                setText(R.id.tvMemoryState, n + " 条");
            } catch (Throwable t) {
                setText(R.id.tvMemoryState, "0 条");
            }

            String sp = UiUtils.getStr(this, "system_prompt", "");
            setText(R.id.tvPromptState, (sp == null || sp.length() == 0)
                    ? LanguageManager.t(ctx, "prompt_default", "默认")
                    : LanguageManager.t(ctx, "prompt_custom", "已自定义"));

            try {
                int aiCount = PluginManager.loadPlugins(this).size();
                int uiCount = PluginManager.loadUiPlugins(this).size();
                int total = aiCount + uiCount;
                setText(R.id.tvPluginState, total == 0
                        ? LanguageManager.t(ctx, "plugin_override_none", "无")
                        : (total + ""));
            } catch (Throwable t) {
                setText(R.id.tvPluginState, "");
            }
        } catch (Throwable t) {}
    }

    private void setText(int id, String text) {
        try {
            TextView tv = (TextView) findViewById(id);
            if (tv != null) {
                tv.setText(text == null ? "" : text);
                try { tv.setTextColor(UiOverrides.outline(this)); } catch (Throwable t) {}
            }
        } catch (Throwable t) {}
    }

    private String shortText(String s, String fallback) {
        if (s == null || s.trim().length() == 0) return fallback;
        s = s.trim();
        if (s.length() > 18) s = s.substring(0, 18) + "…";
        return s;
    }

    private String thinkingShortLabel() {
        final Context ctx = this;
        String cur = UiUtils.getStr(this, "thinking_effort", "");
        if (cur == null || cur.length() == 0) return LanguageManager.t(ctx, "gen_thinking_default", "默认");
        if ("none".equals(cur)) return LanguageManager.t(ctx, "gen_thinking_off", "关闭思考");
        return cur;
    }

    private String themeShortLabel() {
        final Context ctx = this;
        String cur = UiUtils.getStr(this, "theme_style", "system");
        if ("light".equals(cur)) return LanguageManager.t(ctx, "appearance_theme_light", "浅色");
        if ("dark".equals(cur)) return LanguageManager.t(ctx, "appearance_theme_dark", "深色");
        return LanguageManager.t(ctx, "appearance_theme_system", "跟随系统");
    }

    private String languageStateLabel() {
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
}