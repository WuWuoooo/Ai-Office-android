package com.ai.office;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据驱动的 UI 插件对话框。
 *
 * 根据插件的 fields 数组，动态生成输入控件：
 *   · text       单行文本
 *   · multiline  多行文本
 *   · number     数字
 *   · boolean    开关
 *   · select     下拉选择（点击弹 AlertDialog 单选）
 *
 * 用户点「确定」后，把 {key: value} 打包成 JSONObject 回调。
 * 若字段 required 但为空，会提示用户并中止。
 */
public class UiPluginDialog {

    public interface OnSubmitListener {
        void onSubmit(JSONObject args);
    }

    /**
     * 弹出对话框。
     *
     * @param ctx      调用方 Context（必须是 Activity，否则弹窗主题可能异常）
     * @param plugin   从 PluginManager.loadUiPlugins 得到的 JSONObject
     * @param listener 用户点确定后的回调；args 里是 {fieldKey: value}
     */
    public static void show(final Context ctx, final JSONObject plugin, final OnSubmitListener listener) {
        try {
            if (ctx == null || plugin == null) return;

            final String title = plugin.optString("title", plugin.optString("name", "插件"));
            final String desc = plugin.optString("description", "");
            final JSONArray fields = plugin.optJSONArray("fields");

            // 顶层容器
            LinearLayout wrap = new LinearLayout(ctx);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 12),
                            UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 12));

            if (desc.length() > 0) {
                TextView tvDesc = new TextView(ctx);
                tvDesc.setText(desc);
                tvDesc.setTextSize(13);
                tvDesc.setTextColor(UiUtils.color(ctx, R.color.md_outline));
                tvDesc.setPadding(0, 0, 0, UiUtils.dp(ctx, 10));
                wrap.addView(tvDesc);
            }

            // 保存每个字段的控件引用（或者取值方式），用于提交时统一读取
            final Map<String, View> viewByKey = new HashMap<String, View>();
            final Map<String, String> typeByKey = new HashMap<String, String>();
            final Map<String, String> labelByKey = new HashMap<String, String>();
            final List<String> requiredKeys = new ArrayList<String>();

            if (fields != null && fields.length() > 0) {
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject f = fields.optJSONObject(i);
                    if (f == null) continue;
                    String key = f.optString("key", "").trim();
                    if (key.length() == 0) continue;
                    String label = f.optString("label", key);
                    String type = f.optString("type", "text").trim().toLowerCase();
                    boolean required = f.optBoolean("required", false);

                    typeByKey.put(key, type);
                    labelByKey.put(key, label);
                    if (required) requiredKeys.add(key);

                    // 字段标题
                    TextView tvLabel = new TextView(ctx);
                    tvLabel.setText(label + (required ? " *" : ""));
                    tvLabel.setTextSize(13);
                    tvLabel.setTextColor(UiUtils.color(ctx, R.color.md_on_surface));
                    tvLabel.setPadding(0, UiUtils.dp(ctx, 8), 0, UiUtils.dp(ctx, 4));
                    wrap.addView(tvLabel);

                    if ("boolean".equals(type)) {
                        final Switch sw = new Switch(ctx);
                        boolean defVal = f.optBoolean("default", false);
                        sw.setChecked(defVal);
                        wrap.addView(sw);
                        viewByKey.put(key, sw);
                    } else if ("select".equals(type)) {
                        // 用不可编辑的 EditText 展示当前值，点击弹单选
                        final EditText et = new EditText(ctx);
                        et.setTextSize(15);
                        et.setFocusable(false);
                        et.setClickable(true);
                        et.setBackgroundResource(R.drawable.edittext_bg);
                        et.setPadding(UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10),
                                      UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10));

                        // 收集选项
                        final List<String> options = new ArrayList<String>();
                        JSONArray optArr = f.optJSONArray("options");
                        if (optArr != null) {
                            for (int k = 0; k < optArr.length(); k++) {
                                String s = optArr.optString(k, "");
                                if (s.length() > 0) options.add(s);
                            }
                        }
                        if (options.isEmpty()) {
                            // 没有选项则退化为单行文本
                            et.setFocusableInTouchMode(true);
                            et.setInputType(InputType.TYPE_CLASS_TEXT);
                        } else {
                            String def = f.optString("default", options.get(0));
                            et.setText(def);
                            final String fLabel = label;
                            et.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    try {
                                        new AlertDialog.Builder(ctx)
                                                .setTitle(fLabel)
                                                .setItems(options.toArray(new String[options.size()]),
                                                        new DialogInterface.OnClickListener() {
                                                            @Override public void onClick(DialogInterface d, int which) {
                                                                et.setText(options.get(which));
                                                                d.dismiss();
                                                            }
                                                        })
                                                .setNegativeButton("取消", null)
                                                .create().show();
                                    } catch (Throwable t) {}
                                }
                            });
                        }
                        wrap.addView(et);
                        viewByKey.put(key, et);
                    } else if ("number".equals(type)) {
                        EditText et = new EditText(ctx);
                        et.setTextSize(15);
                        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                                | InputType.TYPE_NUMBER_FLAG_SIGNED);
                        et.setBackgroundResource(R.drawable.edittext_bg);
                        et.setPadding(UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10),
                                      UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10));
                        if (f.has("default")) et.setText(String.valueOf(f.opt("default")));
                        if (f.has("hint")) et.setHint(f.optString("hint", ""));
                        wrap.addView(et);
                        viewByKey.put(key, et);
                    } else if ("multiline".equals(type)) {
                        EditText et = new EditText(ctx);
                        et.setTextSize(15);
                        et.setGravity(Gravity.TOP | Gravity.LEFT);
                        et.setMinLines(3);
                        et.setMaxLines(10);
                        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        et.setBackgroundResource(R.drawable.edittext_bg);
                        et.setPadding(UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10),
                                      UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10));
                        if (f.has("default")) et.setText(f.optString("default", ""));
                        if (f.has("hint")) et.setHint(f.optString("hint", ""));
                        wrap.addView(et);
                        viewByKey.put(key, et);
                    } else {
                        // 默认 text
                        EditText et = new EditText(ctx);
                        et.setTextSize(15);
                        et.setInputType(InputType.TYPE_CLASS_TEXT);
                        et.setBackgroundResource(R.drawable.edittext_bg);
                        et.setPadding(UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10),
                                      UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 10));
                        if (f.has("default")) et.setText(f.optString("default", ""));
                        if (f.has("hint")) et.setHint(f.optString("hint", ""));
                        wrap.addView(et);
                        viewByKey.put(key, et);
                    }
                }
            } else {
                TextView tvEmpty = new TextView(ctx);
                tvEmpty.setText("（此插件未定义参数，直接点确定执行）");
                tvEmpty.setTextSize(13);
                tvEmpty.setTextColor(UiUtils.color(ctx, R.color.md_outline));
                wrap.addView(tvEmpty);
            }

            // 内容超过屏幕时用 ScrollView 包裹
            final ScrollView scroll = new ScrollView(ctx);
            scroll.addView(wrap, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            scroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            new AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setView(scroll)
                    .setPositiveButton("执行", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            try {
                                JSONObject args = collectArgs(viewByKey, typeByKey, labelByKey, requiredKeys, ctx);
                                if (args == null) {
                                    // 校验失败（required 为空），提示后不关闭对话框
                                    // 但标准 AlertDialog 一旦点确定就会关，这里改为提示用户重新点
                                    // 简化处理：既然已经关了，就当成取消；用户可重新打开
                                    d.dismiss();
                                    return;
                                }
                                d.dismiss();
                                if (listener != null) listener.onSubmit(args);
                            } catch (Throwable t) {
                                d.dismiss();
                                UiUtils.toast(ctx, "参数收集失败: " + t.getMessage());
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .create().show();
        } catch (Throwable t) {
            UiUtils.toast(ctx, "打开插件对话框失败: " + t.getMessage());
        }
    }

    /** 收集所有字段的值到 JSONObject；有 required 为空则提示并返回 null */
    private static JSONObject collectArgs(Map<String, View> views, Map<String, String> types,
                                          Map<String, String> labels, List<String> required,
                                          Context ctx) {
        try {
            JSONObject args = new JSONObject();
            List<String> missing = new ArrayList<String>();

            for (Map.Entry<String, View> e : views.entrySet()) {
                String key = e.getKey();
                View v = e.getValue();
                String type = types.get(key);
                if (type == null) type = "text";

                Object value = null;
                if (v instanceof Switch) {
                    value = Boolean.valueOf(((Switch) v).isChecked());
                } else if (v instanceof EditText) {
                    String s = ((EditText) v).getText().toString().trim();
                    if ("number".equals(type)) {
                        if (s.length() == 0) {
                            value = Integer.valueOf(0);
                        } else {
                            try {
                                // 有小数点用 Double，否则用 Integer
                                if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
                                    value = Double.valueOf(Double.parseDouble(s));
                                } else {
                                    value = Integer.valueOf(Integer.parseInt(s));
                                }
                            } catch (Throwable t) {
                                value = Integer.valueOf(0);
                            }
                        }
                    } else {
                        value = s;
                    }
                }
                if (value == null) value = "";

                // required 校验（仅对字符串类）
                if (required != null && required.contains(key)) {
                    if (value instanceof String && ((String) value).length() == 0) {
                        String label = labels.get(key);
                        missing.add(label == null ? key : label);
                        continue;
                    }
                }
                args.put(key, value);
            }

            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder("以下必填项不能为空：");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) sb.append("、");
                    sb.append(missing.get(i));
                }
                UiUtils.toast(ctx, sb.toString());
                return null;
            }
            return args;
        } catch (Throwable t) {
            return new JSONObject();
        }
    }
}