package com.ai.office;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

            setupRow(R.id.rowAi, "ai");
            setupRow(R.id.rowVision, "vision");
            setupRow(R.id.rowSearch, "search");
            setupRow(R.id.rowTts, "tts");
            setupRow(R.id.rowRealtime, "realtime");
            setupRow(R.id.rowGenerate, "generate");
            setupRow(R.id.rowAppearance, "appearance");
            setupRow(R.id.rowToggle, "toggle");
            setupRow(R.id.rowMemory, "memory");
            setupRow(R.id.rowPrompt, "prompt");
            setupRow(R.id.rowPlugin, "plugin");
            setupRow(R.id.rowPermission, "permission");
            setupRow(R.id.rowBalance, "balance");
        } catch (Throwable t) {
            Toast.makeText(this, "设置页加载失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
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
                    Toast.makeText(SettingsActivity.this, "无法打开设置: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllStates();
    }

    // ============================================================
    // 刷新每行状态值
    // ============================================================

private void refreshAllStates() {
    try {
        setText(R.id.tvAiState, shortText(UiUtils.getStr(this, "model", "deepseek-flash"), ""));
        setText(R.id.tvVisionState, shortText(UiUtils.getStr(this, "vision_model", ""), "未设置"));
        setText(R.id.tvSearchState, SearchProvider.nameOf(UiUtils.getStr(this, "search_provider", "bing")));

        boolean ttsOn = UiUtils.getBool(this, "tts_enabled", false);
        setText(R.id.tvTtsState, ttsOn ? "已开启" : "已关闭");

        // ★ 新增：实时通话供应商
        String rtProvider = UiUtils.getStr(this, "realtime_provider", "glm-realtime");
        setText(R.id.tvRealtimeState, RealtimeConfig.nameOf(rtProvider));

        setText(R.id.tvGenerateState, thinkingShortLabel());
        setText(R.id.tvAppearanceState, themeShortLabel());

        try {
            int n = MemoryStore.getAllKeys(this).size();
            setText(R.id.tvMemoryState, n + " 条");
        } catch (Throwable t) {
            setText(R.id.tvMemoryState, "0 条");
        }

        String sp = UiUtils.getStr(this, "system_prompt", "");
        setText(R.id.tvPromptState, (sp == null || sp.length() == 0) ? "默认" : "已自定义");

        try {
            int aiCount = PluginManager.loadPlugins(this).size();
            int uiCount = PluginManager.loadUiPlugins(this).size();
            int total = aiCount + uiCount;
            setText(R.id.tvPluginState, total == 0 ? "无" : (total + " 个"));
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
                // 状态值文字色跟随 UI 覆盖（默认取 outline）
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
        String cur = UiUtils.getStr(this, "thinking_effort", "");
        if (cur == null || cur.length() == 0) return "默认";
        if ("none".equals(cur)) return "关闭思考";
        return cur;
    }

    private String themeShortLabel() {
        String cur = UiUtils.getStr(this, "theme_style", "system");
        if ("light".equals(cur)) return "浅色";
        if ("dark".equals(cur)) return "深色";
        return "跟随系统";
    }
}