package com.ai.office;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 自定义对话框，替代系统 AlertDialog。
 *
 * 用法：
 *   new GlassDialog.Builder(ctx)
 *       .setTitle("标题")
 *       .setMessage("消息")
 *       .setPositiveButton("确定", listener)
 *       .setNegativeButton("取消", null)
 *       .show();
 *
 * 支持：标题 / 消息 / 自定义 View（输入框）/ 列表 / 单选列表 / 1~3 个按钮 / dismiss 监听。
 * 外观随 UiOverrides.glassEnabled() 自动切换玻璃 / 普通。
 */
public class GlassDialog {

    private static final int WIDTH_RATIO = 86;    // 弹窗宽度占屏宽
    private static final int MAX_WIDTH_DP = 340;  // 最大宽度
    private static final int LIST_MAX_RATIO = 55; // 列表区最大高度占屏高
    private static final String TAG_MSG = "__glass_msg__";

    // ============================================================
    // 外部 API：动态更新消息
    // ============================================================

    /** 供外部在对话框显示后动态更新消息文字（例如倒计时） */
    public static void updateMessage(Dialog d, String msg) {
        try {
            if (d == null) return;
            View v = d.findViewById(android.R.id.content);
            if (v instanceof ViewGroup) {
                View m = ((ViewGroup) v).findViewWithTag(TAG_MSG);
                if (m instanceof TextView) ((TextView) m).setText(msg);
            }
        } catch (Throwable t) {}
    }

    // ============================================================
    // Builder
    // ============================================================

    public static class Builder {
        private final Context ctx;
        private String title, message;
        private View customView;
        private String[] items;
        private DialogInterface.OnClickListener itemsListener;
        private String[] scItems;
        private int scChecked = -1;
        private DialogInterface.OnClickListener scListener;
        private String posText, negText, neuText;
        private DialogInterface.OnClickListener posL, negL, neuL;
        private boolean cancelable = true;
        private DialogInterface.OnDismissListener dismissL;

        public Builder(Context c) { this.ctx = c; }

        public Builder setTitle(String t) { title = t; return this; }
        public Builder setMessage(String m) { message = m; return this; }
        public Builder setView(View v) { customView = v; return this; }

        public Builder setItems(String[] a, DialogInterface.OnClickListener l) {
            items = a; itemsListener = l; return this;
        }

        public Builder setSingleChoiceItems(String[] a, int checked, DialogInterface.OnClickListener l) {
            scItems = a; scChecked = checked; scListener = l; return this;
        }

        public Builder setPositiveButton(String t, DialogInterface.OnClickListener l) {
            posText = t; posL = l; return this;
        }

        public Builder setNegativeButton(String t, DialogInterface.OnClickListener l) {
            negText = t; negL = l; return this;
        }

        public Builder setNeutralButton(String t, DialogInterface.OnClickListener l) {
            neuText = t; neuL = l; return this;
        }

        public Builder setCancelable(boolean c) { cancelable = c; return this; }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener l) {
            dismissL = l; return this;
        }

        public Dialog show() {
            Dialog d = create();
            try { d.show(); } catch (Throwable t) {}
            return d;
        }

        public Dialog create() {
            final Dialog dialog = new Dialog(ctx);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(cancelable);
            if (dismissL != null) dialog.setOnDismissListener(dismissL);

            boolean glass = UiOverrides.glassEnabled(ctx);
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

            // ---------- 根容器 ----------
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UiUtils.dp(ctx, 22), UiUtils.dp(ctx, 22),
                            UiUtils.dp(ctx, 22), UiUtils.dp(ctx, 10));
            root.setBackgroundDrawable(rootBg(ctx, glass));

            // ---------- 标题 ----------
            if (title != null && title.length() > 0) {
                TextView tv = new TextView(ctx);
                tv.setText(title);
                tv.setTextSize(18);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(UiOverrides.onSurface(ctx));
                tv.setPadding(0, 0, 0, UiUtils.dp(ctx, 10));
                root.addView(tv);
            }

            // ---------- 消息 ----------
            if (message != null && message.length() > 0) {
                TextView tv = new TextView(ctx);
                tv.setTag(TAG_MSG);
                tv.setText(message);
                tv.setTextSize(15);
                tv.setLineSpacing(UiUtils.dp(ctx, 4), 1f);
                tv.setTextColor(UiOverrides.onSurface(ctx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = UiUtils.dp(ctx, 8);
                root.addView(tv, lp);
            }

            // ---------- 自定义 View（如输入框） ----------
            if (customView != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = UiUtils.dp(ctx, 6);
                lp.bottomMargin = UiUtils.dp(ctx, 8);
                root.addView(customView, lp);
            }

            // ---------- 单选列表 ----------
            if (scItems != null && scItems.length > 0) {
                root.addView(buildList(dialog, ctx, glass, dm, scItems, scChecked, scListener, null));
            }
            // ---------- items 列表 ----------
            else if (items != null && items.length > 0) {
                root.addView(buildList(dialog, ctx, glass, dm, items, -1, null, itemsListener));
            }

            // ---------- 按钮行 ----------
            if (posText != null || negText != null || neuText != null) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                row.setPadding(0, UiUtils.dp(ctx, 12), 0, 0);
                row.setTag("__glass_btnrow__");

                if (neuText != null) {
                    row.addView(makeButton(ctx, neuText, UiOverrides.outline(ctx), glass,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (neuL != null) neuL.onClick(dialog, DialogInterface.BUTTON_NEUTRAL);
                                    else dialog.dismiss();
                                }
                            }));
                }
                if (negText != null) {
                    row.addView(makeButton(ctx, negText, UiOverrides.primary(ctx), glass,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (negL != null) negL.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                                    else dialog.dismiss();
                                }
                            }));
                }
                if (posText != null) {
                    row.addView(makeButton(ctx, posText, UiOverrides.primary(ctx), glass,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (posL != null) posL.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                                    else dialog.dismiss();
                                }
                            }));
                }
                root.addView(row);
            }

            dialog.setContentView(root);

            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int byRatio = (int) (dm.widthPixels * WIDTH_RATIO / 100f);
                int byMax = UiUtils.dp(ctx, MAX_WIDTH_DP);
                w.setLayout(Math.min(byRatio, byMax), WindowManager.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.CENTER);
                try { w.setDimAmount(0.4f); } catch (Throwable t) {}
            }

            return dialog;
        }

        // ============================================================
        // 兼容 AlertDialog.getButton()：通过按钮行的位置查找
        // ============================================================

        /** 从 Dialog 里找到按钮 TextView。
         *  which: BUTTON_POSITIVE(-1) / BUTTON_NEGATIVE(-2) / BUTTON_NEUTRAL(-3) */
        public static TextView getButton(Dialog dialog, int which) {
            try {
                if (dialog == null) return null;
                View v = dialog.findViewById(android.R.id.content);
                if (!(v instanceof ViewGroup)) return null;
                View row = findTagged((ViewGroup) v, "__glass_btnrow__");
                if (!(row instanceof ViewGroup)) return null;
                ViewGroup g = (ViewGroup) row;
                int n = g.getChildCount();
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    return n >= 1 ? (TextView) g.getChildAt(n - 1) : null;
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    return n >= 2 ? (TextView) g.getChildAt(n - 2) : null;
                } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                    return n >= 3 ? (TextView) g.getChildAt(0) : null;
                }
            } catch (Throwable t) {}
            return null;
        }

        private static View findTagged(ViewGroup vg, Object tag) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                View v = vg.getChildAt(i);
                if (tag.equals(v.getTag())) return v;
                if (v instanceof ViewGroup) {
                    View r = findTagged((ViewGroup) v, tag);
                    if (r != null) return r;
                }
            }
            return null;
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private static View buildList(final Dialog dialog, final Context ctx, final boolean glass,
                                  DisplayMetrics dm, final String[] arr, final int checkedIdx,
                                  final DialogInterface.OnClickListener scL,
                                  final DialogInterface.OnClickListener itemL) {
        int maxH = dm.heightPixels * LIST_MAX_RATIO / 100;
        MaxHeightScrollView sv = new MaxHeightScrollView(ctx, maxH);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < arr.length; i++) {
            final int which = i;
            final boolean checked = (i == checkedIdx);

            TextView item = new TextView(ctx);
            item.setText((checked ? "✓   " : "      ") + arr[i]);
            item.setTextSize(15);
            item.setTextColor(checked ? UiOverrides.primary(ctx) : UiOverrides.onSurface(ctx));
            item.setPadding(UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 12),
                            UiUtils.dp(ctx, 12), UiUtils.dp(ctx, 12));
            item.setSingleLine(true);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundDrawable(itemBg(ctx, glass));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (scL != null) scL.onClick(dialog, which);
                    else {
                        dialog.dismiss();
                        if (itemL != null) itemL.onClick(dialog, which);
                    }
                }
            });

            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(0, UiUtils.dp(ctx, 1), 0, UiUtils.dp(ctx, 1));
            list.addView(item, ilp);
        }

        sv.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = UiUtils.dp(ctx, 4);
        sv.setLayoutParams(slp);
        return sv;
    }

    private static TextView makeButton(Context ctx, String text, int color, boolean glass,
                                       View.OnClickListener l) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(color);
        btn.setPadding(UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 10),
                       UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 10));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setSingleLine(true);
        btn.setBackgroundDrawable(itemBg(ctx, glass));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiUtils.dp(ctx, 4), 0, 0, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private static class MaxHeightScrollView extends ScrollView {
        private final int maxH;
        MaxHeightScrollView(Context c, int h) { super(c); this.maxH = h; }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        }
    }

    private static Drawable rootBg(Context ctx, boolean glass) {
        if (glass) return UiOverrides.cardBgDrawable(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(UiOverrides.surface(ctx));
        d.setCornerRadius(UiUtils.dp(ctx, 22));
        d.setStroke(UiUtils.dp(ctx, 1), UiOverrides.divider(ctx));
        return d;
    }

    private static Drawable itemBg(Context ctx, boolean glass) {
        int radius = UiUtils.dp(ctx, 12);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(0x00000000);
        normal.setCornerRadius(radius);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(glass ? 0x1AFFFFFF : 0x12000000);
        pressed.setCornerRadius(radius);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}