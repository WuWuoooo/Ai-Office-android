package com.ai.office;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownView {

    private final Context ctx;
    private final float baseSp;

    // 颜色优先从 UiOverrides 取（插件可覆盖），没有覆盖时自动回落到 colors.xml
    private final int cText;
    private final int cSub;
    private final int cGrid;
    private final int cThead;
    private final int cTableBody;
    private final int cQuoteBar;
    private final int cCodeText;
    private final int cPrimary;

    private MarkdownView(Context c, float baseSp) {
        this.ctx = c;
        this.baseSp = baseSp;
        cText      = UiOverrides.onSurface(c);
        cSub       = UiOverrides.outline(c);
        cGrid      = UiOverrides.divider(c);
        cThead     = UiOverrides.cardBg(c);
        cTableBody = UiOverrides.surface(c);
        cQuoteBar  = UiOverrides.outline(c);
        cCodeText  = UiOverrides.codeText(c);
        cPrimary   = UiOverrides.primary(c);
    }

    public static View render(Context c, String md) {
        return render(c, md, 15f);
    }

    public static View render(Context c, String md, float baseSp) {
        if (md == null) md = "";
        // 若用户在 UI 覆盖里设置了 ui_size_text，优先使用它
        try {
            String ov = UiUtils.getStr(c, "ui_size_text", "");
            if (ov != null && ov.trim().length() > 0) {
                float v = Float.parseFloat(ov.trim());
                if (v > 0 && v <= 200) baseSp = v;
            }
        } catch (Throwable t) {}

        // 安全防护：超长文本直接返回纯文本 TextView，防止正则回溯导致卡顿/崩溃
        if (md.length() > 20000) {
            TextView tv = new TextView(c);
            tv.setText(md);
            tv.setTextSize(baseSp);
            tv.setTextColor(UiOverrides.onSurface(c));
            tv.setTextIsSelectable(true);
            return tv;
        }
        return new MarkdownView(c, baseSp).build(md);
    }

    public static void copyText(Context c, String text) {
        if (c == null || text == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ai_office", text));
                Toast.makeText(c, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {}
    }

    private View build(String md) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String norm = md.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = norm.split("\n", -1);
        StringBuilder para = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String raw = lines[i];
            String t = raw.trim();

            if (t.startsWith("```")) {
                flushParagraph(root, para);
                i++;
                StringBuilder code = new StringBuilder();
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]);
                    if (i < lines.length - 1) code.append('\n');
                    i++;
                }
                if (i < lines.length) i++;
                addCode(root, code.toString());
                continue;
            }

            if (isTableHeader(lines, i)) {
                flushParagraph(root, para);
                i = addTable(root, lines, i);
                continue;
            }

            int hl = headingLevel(t);
            if (hl > 0) {
                flushParagraph(root, para);
                addHeading(root, t.substring(hl).trim(), hl);
                i++;
                continue;
            }

            if (isHr(t)) {
                flushParagraph(root, para);
                addHr(root);
                i++;
                continue;
            }

            if (t.startsWith(">")) {
                flushParagraph(root, para);
                i = addQuote(root, lines, i);
                continue;
            }

            if (isListLine(raw)) {
                flushParagraph(root, para);
                i = addList(root, lines, i);
                continue;
            }

            if (t.length() == 0) {
                flushParagraph(root, para);
                i++;
                continue;
            }

            if (para.length() > 0) para.append('\n');
            para.append(t);
            i++;
        }
        flushParagraph(root, para);
        return root;
    }

    private void flushParagraph(LinearLayout root, StringBuilder para) {
        if (para.length() == 0) return;
        String text = para.toString();
        para.setLength(0);
        if (text.trim().length() == 0) return;

        TextView tv = new TextView(ctx);
        setHtml(tv, inlineMulti(text));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp);
        tv.setTextColor(cText);
        tv.setLineSpacing(dp(4), 1f);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, dp(3), 0, dp(3));
        root.addView(tv, matchWrap());
    }

    private void addHeading(LinearLayout root, String text, int level) {
        TextView tv = new TextView(ctx);
        setHtml(tv, inline(text));
        float size;
        if (level == 1) size = baseSp + 7f;
        else if (level == 2) size = baseSp + 5f;
        else if (level == 3) size = baseSp + 3f;
        else size = baseSp + 1f;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(cText);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, dp(8), 0, dp(4));
        root.addView(tv, matchWrap());
    }

    private void addHr(LinearLayout root) {
        View v = new View(ctx);
        v.setBackgroundColor(cGrid);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(8), 0, dp(8));
        root.addView(v, lp);
    }

    private int addQuote(LinearLayout root, String[] lines, int i) {
        StringBuilder q = new StringBuilder();
        while (i < lines.length && lines[i].trim().startsWith(">")) {
            String s = lines[i].trim();
            if (s.startsWith(">")) s = s.substring(1).trim();
            if (q.length() > 0) q.append('\n');
            q.append(s);
            i++;
        }
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(5), 0, dp(5));
        box.setLayoutParams(blp);

        View bar = new View(ctx);
        bar.setBackgroundColor(cQuoteBar);
        box.addView(bar, new LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT));

        TextView tv = new TextView(ctx);
        setHtml(tv, inlineMulti(q.toString()));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp);
        tv.setTextColor(cSub);
        tv.setLineSpacing(dp(4), 1f);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(10), dp(2), 0, dp(2));
        box.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(box);
        return i;
    }

    private int addList(LinearLayout root, String[] lines, int i) {
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        list.setLayoutParams(lp);

        while (i < lines.length) {
            String raw = lines[i];
            if (!isListLine(raw)) break;
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
            String t = raw.trim();
            String prefix;
            String body;
            int dot = orderedMarkerEnd(t);
            if (dot >= 0) {
                prefix = t.substring(0, dot + 1);
                body = t.substring(dot + 1).trim();
            } else {
                prefix = indent >= 2 ? "\u25E6" : "\u2022";
                body = t.length() >= 2 ? t.substring(2).trim() : "";
            }

            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(prefix).append("  ");
            try { sb.append(Html.fromHtml(inline(body))); } catch (Throwable e) { sb.append(body); }

            TextView tv = new TextView(ctx);
            tv.setText(sb);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp);
            tv.setTextColor(cText);
            tv.setLineSpacing(dp(4), 1f);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(6) + (indent >= 2 ? dp(16) : 0), dp(3), 0, dp(3));
            list.addView(tv, matchWrap());
            i++;
        }
        root.addView(list);
        return i;
    }

    private void addCode(LinearLayout root, String code) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.code_bg);
        box.setPadding(dp(10), dp(6), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        box.setLayoutParams(lp);

        final TextView codeTv = new TextView(ctx);
        codeTv.setText(code);
        codeTv.setTypeface(Typeface.MONOSPACE);
        codeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp - 2f);
        codeTv.setTextColor(cCodeText);
        codeTv.setTextIsSelectable(true);
        codeTv.setHorizontallyScrolling(true);
        codeTv.setLineSpacing(dp(3), 1f);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.RIGHT);
        TextView copy = new TextView(ctx);
        copy.setText("复制代码");
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        copy.setTextColor(cPrimary);
        copy.setPadding(dp(8), dp(4), dp(2), dp(6));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyText(ctx, codeTv.getText().toString()); }
        });
        bar.addView(copy);
        box.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(codeTv, new android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(hs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(box);
    }

    private boolean isTableHeader(String[] lines, int i) {
        if (i + 1 >= lines.length) return false;
        String a = lines[i];
        String bs = lines[i + 1].trim();
        if (a.indexOf('|') < 0) return false;
        if (bs.length() < 3) return false;
        if (bs.indexOf('-') < 0) return false;
        for (int k = 0; k < bs.length(); k++) {
            char c = bs.charAt(k);
            if (c != '|' && c != '-' && c != ':' && c != ' ') return false;
        }
        return true;
    }

    private int addTable(LinearLayout root, String[] lines, int i) {
        String[] header = splitRow(lines[i]);
        String[] sep = splitRow(lines[i + 1]);
        boolean[] alignRight = new boolean[header.length];
        boolean[] alignCenter = new boolean[header.length];
        for (int k = 0; k < sep.length && k < header.length; k++) {
            String s = sep[k].trim();
            boolean l = s.startsWith(":");
            boolean r = s.endsWith(":");
            if (l && r) alignCenter[k] = true;
            else if (r) alignRight[k] = true;
        }

        List<String[]> rows = new ArrayList<String[]>();
        int j = i + 2;
        while (j < lines.length) {
            String t = lines[j].trim();
            if (t.length() == 0 || t.indexOf('|') < 0) break;
            rows.add(splitRow(lines[j]));
            j++;
        }

        LinearLayout table = new LinearLayout(ctx);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackgroundColor(cGrid);
        table.setPadding(dp(1), dp(1), dp(1), dp(1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(6), 0, dp(6));
        table.setLayoutParams(tlp);

        table.addView(buildRow(header, true, header.length, alignRight, alignCenter));
        for (int k = 0; k < rows.size(); k++) {
            table.addView(buildRow(rows.get(k), false, header.length, alignRight, alignCenter));
        }
        root.addView(table);
        return j;
    }

    private View buildRow(String[] cells, boolean head, int cols,
                          boolean[] alignRight, boolean[] alignCenter) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int k = 0; k < cols; k++) {
            String c = k < cells.length ? cells[k] : "";
            TextView tv = new TextView(ctx);
            setHtml(tv, inline(c));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp - 1f);
            tv.setPadding(dp(8), dp(7), dp(8), dp(7));
            tv.setTextColor(cText);
            if (head) {
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setBackgroundColor(cThead);
                tv.setGravity(Gravity.CENTER);
            } else {
                tv.setBackgroundColor(cTableBody);
                if (k < alignCenter.length && alignCenter[k]) tv.setGravity(Gravity.CENTER);
                else if (k < alignRight.length && alignRight[k]) tv.setGravity(Gravity.RIGHT);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(k == 0 ? 0 : dp(1), 0, 0, dp(1));
            row.addView(tv, lp);
        }
        return row;
    }

    private String[] splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split("\\|", -1);
        for (int k = 0; k < parts.length; k++) parts[k] = parts[k].trim();
        return parts;
    }

    private String inlineMulti(String s) {
        String[] ls = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.length; i++) {
            if (i > 0) sb.append("<br/>");
            sb.append(inline(ls[i]));
        }
        return sb.toString();
    }

    private String inline(String s) {
        if (s == null) return "";
        List<String> codes = new ArrayList<String>();
        StringBuilder raw = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    codes.add(s.substring(i + 1, end));
                    raw.append('\u0001').append(codes.size() - 1).append('\u0001');
                    i = end + 1;
                    continue;
                }
            }
            raw.append(c);
            i++;
        }
        String t = escape(raw.toString());
        t = replaceLinks(t);
        t = replaceGroup(t, "\\*\\*(.+?)\\*\\*", "<b>", "</b>");
        t = replaceGroup(t, "__.+?__", "<b>", "</b>");
        t = replaceGroup(t, "\\*([^*\\n]+)\\*", "<i>", "</i>");
        t = replaceGroup(t, "~~.+?~~", "", "");
        for (int k = 0; k < codes.size(); k++) {
            t = t.replace("\u0001" + k + "\u0001", "<tt>" + escape(codes.get(k)) + "</tt>");
        }
        return t;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String replaceGroup(String input, String regex, String prefix, String suffix) {
        if (input == null) return "";
        try {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
            Matcher m = p.matcher(input);
            if (!m.find()) return input;
            StringBuffer out = new StringBuffer();
            do {
                m.appendReplacement(out, "");
                out.append(prefix).append(m.group(1)).append(suffix);
            } while (m.find());
            m.appendTail(out);
            return out.toString();
        } catch (Throwable t) { return input; }
    }

    private static String replaceLinks(String input) {
        if (input == null) return "";
        try {
            Pattern p = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
            Matcher m = p.matcher(input);
            if (!m.find()) return input;
            StringBuffer out = new StringBuffer();
            do {
                m.appendReplacement(out, "");
                out.append("<a href=\"").append(m.group(2)).append("\">")
                        .append(m.group(1)).append("</a>");
            } while (m.find());
            m.appendTail(out);
            return out.toString();
        } catch (Throwable t) { return input; }
    }

    private void setHtml(TextView tv, String html) {
        try { tv.setText(Html.fromHtml(html)); } catch (Throwable t) { tv.setText(html); }
    }

    private int headingLevel(String t) {
        if (t.length() == 0 || t.charAt(0) != '#') return 0;
        int k = 0;
        while (k < t.length() && t.charAt(k) == '#' && k < 6) k++;
        if (k == 0 || k >= t.length()) return 0;
        if (t.charAt(k) != ' ') return 0;
        return k;
    }

    private boolean isHr(String t) {
        if (t.length() < 3) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int k = 0; k < t.length(); k++) {
            if (t.charAt(k) != c) return false;
        }
        return true;
    }

    private boolean isListLine(String raw) {
        if (raw == null || raw.length() == 0) return false;
        String t = raw.trim();
        if (t.length() < 2) return false;
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) return true;
        return orderedMarkerEnd(t) >= 0;
    }

    private int orderedMarkerEnd(String t) {
        if (t == null) return -1;
        int k = 0;
        while (k < t.length() && Character.isDigit(t.charAt(k))) k++;
        if (k == 0 || k >= t.length()) return -1;
        char c = t.charAt(k);
        if ((c == '.' || c == ')') && k + 1 < t.length() && t.charAt(k + 1) == ' ') return k;
        return -1;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}