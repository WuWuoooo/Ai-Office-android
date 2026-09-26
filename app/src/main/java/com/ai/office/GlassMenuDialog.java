package com.ai.office;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 自定义菜单弹窗。
 *
 * - 标准模式（showItems）：宽度屏宽 86%，最大 320dp，居中显示
 * - 紧凑模式（showCompactItems / showCompactItemsAt）：
 *   宽度屏宽 60%（上限 240dp，下限 180dp），每项右侧可显示 trailing 提示（✓ / › 等）
 * - 紧凑锚定模式（showCompactItemsAt）：在 anchor view 下方展开 + 弹出/收起动画
 * - 高度内容自适应，列表超过屏高 55% 时内部滚动
 * - 玻璃 / 普通双样式
 */
public class GlassMenuDialog {

    private static final int MAX_LIST_H_RATIO = 55;
    private static final int WIDTH_RATIO = 86;
    private static final int MAX_WIDTH_DP = 320;

    private static final int COMPACT_WIDTH_RATIO = 60;
    private static final int COMPACT_MAX_WIDTH_DP = 240;
    private static final int COMPACT_MIN_WIDTH_DP = 180;

    // ============================================================
    // 标准模式
    // ============================================================

    public static void showItems(final Context ctx, String title, final String[] items,
                                 final DialogInterface.OnClickListener listener) {
        showItems(ctx, title, items, listener, null, null);
    }

    public static void showItems(final Context ctx, String title, final String[] items,
                                 final DialogInterface.OnClickListener listener,
                                 String negativeText,
                                 final DialogInterface.OnClickListener negativeListener) {
        if (ctx == null || items == null || items.length == 0) return;

        final boolean glass = UiOverrides.glassEnabled(ctx);
        final DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(ctx, 6);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundDrawable(menuBgDrawable(ctx, glass));

        if (title != null && title.length() > 0) {
            TextView tvTitle = new TextView(ctx);
            tvTitle.setText(title);
            tvTitle.setTextSize(14);
            tvTitle.setTextColor(UiOverrides.outline(ctx));
            tvTitle.setPadding(UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 12),
                               UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 4));
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            root.addView(tvTitle);
        }

        int maxListH = dm.heightPixels * MAX_LIST_H_RATIO / 100;
        MaxHeightScrollView sv = new MaxHeightScrollView(ctx, maxListH);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView item = new TextView(ctx);
            item.setText(items[i]);
            item.setTextSize(15);
            item.setTextColor(UiOverrides.onSurface(ctx));
            item.setPadding(UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 13),
                            UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 13));
            item.setClickable(true);
            item.setFocusable(true);
            item.setSingleLine(true);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setBackgroundDrawable(itemBgDrawable(ctx, glass));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) listener.onClick(dialog, which);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiUtils.dp(ctx, 1), 0, UiUtils.dp(ctx, 1));
            list.addView(item, lp);
        }

        sv.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView cancel = new TextView(ctx);
        cancel.setText(negativeText == null
                ? LanguageManager.t(ctx, "common_cancel", "取消") : negativeText);
        cancel.setTextSize(15);
        cancel.setTextColor(UiOverrides.primary(ctx));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 12),
                          UiUtils.dp(ctx, 16), UiUtils.dp(ctx, 12));
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setBackgroundDrawable(itemBgDrawable(ctx, glass));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                if (negativeListener != null)
                    negativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, UiUtils.dp(ctx, 4), 0, 0);
        root.addView(cancel, clp);

        dialog.setContentView(root);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int wByRatio = (int) (dm.widthPixels * WIDTH_RATIO / 100f);
            int wMax = UiUtils.dp(ctx, MAX_WIDTH_DP);
            int width = Math.min(wByRatio, wMax);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            try { w.setDimAmount(0.4f); } catch (Throwable t) {}
        }

        try { dialog.show(); } catch (Throwable t) {}
    }

    // ============================================================
    // 紧凑模式（居中，无锚点）
    // ============================================================

    public static void showCompactItems(final Context ctx, String title,
                                        final String[] items,
                                        final DialogInterface.OnClickListener listener) {
        showCompactItemsAt(ctx, null, title, items, null, listener);
    }

    public static void showCompactItems(final Context ctx, String title,
                                        final String[] items,
                                        final String[] trailing,
                                        final DialogInterface.OnClickListener listener) {
        showCompactItemsAt(ctx, null, title, items, trailing, listener);
    }

    // ============================================================
    // 紧凑模式（锚定到 anchor view 下方展开 + 弹出/收起动画）
    // ============================================================

    /**
     * @param anchor   锚点 View；null 时退化为居中显示
     * @param trailing 与 items 等长；某项为 null 或空串则右侧不显示提示
     */
    public static void showCompactItemsAt(final Context ctx, final View anchor, String title,
                                          final String[] items, final String[] trailing,
                                          final DialogInterface.OnClickListener listener) {
        if (ctx == null || items == null || items.length == 0) return;

        final boolean glass = UiOverrides.glassEnabled(ctx);
        final DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(ctx, 5);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundDrawable(menuBgDrawable(ctx, glass));

        if (title != null && title.length() > 0) {
            TextView tvTitle = new TextView(ctx);
            tvTitle.setText(title);
            tvTitle.setTextSize(13);
            tvTitle.setTextColor(UiOverrides.outline(ctx));
            tvTitle.setPadding(UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 10),
                               UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 2));
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            root.addView(tvTitle);
        }

        int maxListH = dm.heightPixels * MAX_LIST_H_RATIO / 100;
        MaxHeightScrollView sv = new MaxHeightScrollView(ctx, maxListH);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int which = i;
            String label = items[i];
            String tail = (trailing != null && i < trailing.length) ? trailing[i] : null;

            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 10),
                            UiUtils.dp(ctx, 14), UiUtils.dp(ctx, 10));
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundDrawable(itemBgDrawable(ctx, glass));

            TextView tvText = new TextView(ctx);
            tvText.setText(label == null ? "" : label);
            tvText.setTextSize(15);
            tvText.setTextColor(UiOverrides.onSurface(ctx));
            tvText.setSingleLine(true);
            tvText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            item.addView(tvText, textLp);

            if (tail != null && tail.length() > 0) {
                TextView tvTail = new TextView(ctx);
                tvTail.setText(tail);
                tvTail.setTextSize(15);
                tvTail.setTextColor(UiOverrides.primary(ctx));
                tvTail.setPadding(UiUtils.dp(ctx, 8), 0, 0, 0);
                item.addView(tvTail);
            }

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(final View v) {
                    // 收起动画
                    root.animate()
                            .alpha(0f)
                            .scaleX(0.9f)
                            .scaleY(0.9f)
                            .setDuration(120L)
                            .withEndAction(new Runnable() {
                                @Override public void run() {
                                    try { dialog.dismiss(); } catch (Throwable t) {}
                                    if (listener != null)
                                        listener.onClick(dialog, which);
                                }
                            })
                            .start();
                }
            });

            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(0, UiUtils.dp(ctx, 1), 0, UiUtils.dp(ctx, 1));
            list.addView(item, ilp);
        }

        sv.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);

        // 计算菜单宽度
        int wByRatio = (int) (dm.widthPixels * COMPACT_WIDTH_RATIO / 100f);
        int wMax = UiUtils.dp(ctx, COMPACT_MAX_WIDTH_DP);
        int wMin = UiUtils.dp(ctx, COMPACT_MIN_WIDTH_DP);
        int menuW = Math.min(wByRatio, wMax);
        if (menuW < wMin) menuW = wMin;

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            try { w.setDimAmount(0.25f); } catch (Throwable t) {}
        }

        try { dialog.show(); } catch (Throwable t) { return; }

        if (w == null) return;

        // ★ 让 dialog window 使用绝对屏幕坐标（含状态栏区域），
        //   这样 anchor.getLocationOnScreen() 得到的 y 可以直接用作 lp.y
        try {
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                     | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Throwable t) {}

        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = menuW;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        if (anchor != null) {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            int anchorX = loc[0];
            int anchorY = loc[1];
            int anchorW = anchor.getWidth();
            int anchorH = anchor.getHeight();

            int margin = UiUtils.dp(ctx, 8);
            int x = anchorX + anchorW / 2 - menuW / 2;
            if (x < margin) x = margin;
            if (x + menuW > dm.widthPixels - margin) x = dm.widthPixels - margin - menuW;
            int y = anchorY + anchorH + UiUtils.dp(ctx, 6);

            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = x;
            lp.y = y;
        } else {
            lp.gravity = Gravity.CENTER;
            lp.x = 0;
            lp.y = 0;
        }
        w.setAttributes(lp);

        // 弹出动画：从顶部中央向外展开
        root.setPivotX(menuW / 2f);
        root.setPivotY(0f);
        root.setScaleX(0.7f);
        root.setScaleY(0.7f);
        root.setAlpha(0f);
        root.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(200L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ============================================================
    // 内部
    // ============================================================

    private static class MaxHeightScrollView extends ScrollView {
        private final int maxHeightPx;
        MaxHeightScrollView(Context c, int maxH) { super(c); this.maxHeightPx = maxH; }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int limited = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
            super.onMeasure(wSpec, limited);
        }
    }

    private static Drawable menuBgDrawable(Context ctx, boolean glass) {
        if (glass) return UiOverrides.cardBgDrawable(ctx);

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(UiOverrides.surface(ctx));
        d.setCornerRadius(UiUtils.dp(ctx, 20));
        d.setStroke(UiUtils.dp(ctx, 1), UiOverrides.divider(ctx));
        return d;
    }

    private static Drawable itemBgDrawable(Context ctx, boolean glass) {
        int radius = UiUtils.dp(ctx, 14);
        if (glass) {
            GradientDrawable normal = new GradientDrawable();
            normal.setShape(GradientDrawable.RECTANGLE);
            normal.setColor(0x00000000);
            normal.setCornerRadius(radius);

            GradientDrawable pressed = new GradientDrawable();
            pressed.setShape(GradientDrawable.RECTANGLE);
            pressed.setColor(0x1AFFFFFF);
            pressed.setCornerRadius(radius);

            StateListDrawable sld = new StateListDrawable();
            sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
            sld.addState(new int[]{}, normal);
            return sld;
        }

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(0x00000000);
        normal.setCornerRadius(radius);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(0x12000000);
        pressed.setCornerRadius(radius);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}