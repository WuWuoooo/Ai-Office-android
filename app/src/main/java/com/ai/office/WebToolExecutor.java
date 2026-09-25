package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网工具：网页抓取 + 联网搜索（原生 HttpURLConnection，无第三方库）
 *
 * - fetch_url   抓取指定 URL。若配置了智谱 API Key 且搜索提供商为智谱 → 走智谱 reader；否则原生抓 HTML
 * - web_search  联网搜索。按设置里的「搜索提供商」选择引擎：
 *      · bing / duckduckgo  → 免费内置引擎（RSS / HTML 解析）
 *      · zhipu / tavily / brave / serper / searxng → 按 SearchProvider 预设发请求并解析 JSON
 *      · custom             → 用户自定义 URL / 方法 / 请求体 / 鉴权 / 字段映射
 *
 * 结构化结果通过 @@SEARCH@@ / @@READER@@ 前缀返回，App 端渲染成卡片。
 */
public class WebToolExecutor {

    private static final int MAX_PAGE_BYTES = 1024 * 1024;
    private static final int MAX_TEXT = 50000;
    private static final int MAX_RESULTS = 10;

    public static final String SEARCH_PREFIX = "@@SEARCH@@";
    public static final String READER_PREFIX = "@@READER@@";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private final Context ctx;

    public WebToolExecutor(Context ctx) {
        this.ctx = ctx;
    }

    public String execute(String tool, String argsJson) {
        try {
            if (argsJson == null || argsJson.trim().length() == 0) argsJson = "{}";
            JSONObject args = new JSONObject(argsJson);
            if ("fetch_url".equals(tool)) return fetchUrl(args.optString("url", ""));
            if ("web_search".equals(tool)) return webSearch(args.optString("query", ""));
            return "未知联网工具: " + tool;
        } catch (Throwable t) {
            return "联网工具执行失败: " + t.getMessage();
        }
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
    }

    // ============================================================
    // 供应商参数读取（custom 走 prefs，其它走 SearchProvider 预设）
    // ============================================================

    private String providerId() {
        try {
            String v = prefs().getString("search_provider", "bing");
            if (v == null || v.trim().length() == 0) return "bing";
            return v.trim();
        } catch (Throwable t) { return "bing"; }
    }

    private String providerUrl(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_url", ""));
        String u = SearchProvider.urlOf(id);
        // 若用户在设置里覆盖了 URL，优先用覆盖值
        String override = prefs().getString("search_override_url_" + id, "");
        if (override != null && override.trim().length() > 0) return override.trim();
        return u == null ? "" : u;
    }

    private String providerMethod(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_method", "GET")).trim();
        String m = SearchProvider.methodOf(id);
        return (m == null || m.length() == 0) ? "GET" : m;
    }

    private String providerBody(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_body", ""));
        String b = SearchProvider.bodyOf(id);
        String override = prefs().getString("search_override_body_" + id, "");
        if (override != null && override.trim().length() > 0) return override.trim();
        return b == null ? "" : b;
    }

    private String providerAuthHeader(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_auth_header", "")).trim();
        return nvl(SearchProvider.authHeaderOf(id));
    }

    private String providerAuthPrefix(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_auth_prefix", "")).trim();
        return nvl(SearchProvider.authPrefixOf(id));
    }

    private String providerResultPath(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_result_path", "")).trim();
        return nvl(SearchProvider.resultPathOf(id));
    }

    private String providerTitleKey(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_title_key", "title")).trim();
        return nvl(SearchProvider.titleKeyOf(id));
    }

    private String providerContentKey(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_content_key", "content")).trim();
        return nvl(SearchProvider.contentKeyOf(id));
    }

    private String providerLinkKey(String id) {
        if ("custom".equals(id)) return nvl(prefs().getString("search_custom_link_key", "url")).trim();
        return nvl(SearchProvider.linkKeyOf(id));
    }

    private String providerApiKey() {
        // 优先用搜索专用 Key，其次用主 API Key
        String k = prefs().getString("search_api_key", "");
        if (k != null && k.trim().length() > 0) return k.trim();
        String k2 = prefs().getString("api_key", "");
        return k2 == null ? "" : k2.trim();
    }

    private int providerCount() {
        try {
            int n = Integer.parseInt(prefs().getString("search_count", "10"));
            if (n < 1) n = 1;
            if (n > 50) n = 50;
            return n;
        } catch (Throwable t) { return 10; }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    
    /** 常见中文字（用于识别乱码）：UTF-8 被当作 GBK 解码时，结果中几乎不出现这些字 */
private static final String COMMON_CN_CHARS =
        "的一是了我不人在他有这上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美总从无情己面最女但现前些所同日手又行意动方期它头经长儿回位分爱老因很给名法间斯知世什两次使身者被高已亲其进此话常与活正感你也很就是东西南北时候问题地方网络搜索工具结果显示";

/** 判断字符串是否可能是 UTF-8 被误解码的乱码：CJK 字符多但几乎都是非常用字 */
/** 判断字符串是否可能是 UTF-8 被误解码的乱码：CJK 字符多但几乎都是非常用字 */
private static boolean isLikelyGarbled(String s) {
    if (s == null || s.length() < 8) return false;
    int cn = 0, common = 0, weird = 0, ascii = 0;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c >= 0x4E00 && c <= 0x9FFF) {
            cn++;
            if (COMMON_CN_CHARS.indexOf(c) >= 0) common++;
            else if (isGarbledMarker(c)) weird++;
        } else if (c < 128) {
            ascii++;
        }
    }
    if (cn < 5) return false;
    // ① 命中 2 个以上典型乱码标记字 → 判为乱码
    if (weird >= 2) return true;
    // ② 常用字占比 < 25% 且 ASCII 字符占比 < 5% → 判为乱码
    if (common * 100 < cn * 25 && ascii * 100 < s.length() * 5) return true;
    return false;
}

/** GBK 误解码 UTF-8 时的高频产物字符（正常中文里极少出现） */
private static boolean isGarbledMarker(char c) {
    // 用 Unicode 码点判断，避免源码里出现这些字本身
    // 锟(9535) 斤(65A4) 拷(62F7) 浜(6D5C) 烘(70D8) 姟(59DF)
    // 绮(7EEE) 娣(5A23) 鐨(9428) 勬(52F0) 鏄(93C4) 剧(5267)
    // ず(305A) 鏃(93C3) 跺(8DFA) 欎(6B0E) 笉(7B09) 浠(6D60)
    // 繘(7E58) 涓(6D93) 浗(6D57) 鍟(935F) 婊(5A4A) 鍟(935F)
    return c == '\u9535' || c == '\u65A4' || c == '\u62F7'
        || c == '\u6D5C' || c == '\u70D8' || c == '\u59DF'
        || c == '\u7EEE' || c == '\u5A23' || c == '\u9428'
        || c == '\u52F0' || c == '\u93C4' || c == '\u5267'
        || c == '\u305A' || c == '\u93C3' || c == '\u8DFA'
        || c == '\u6B0E' || c == '\u7B09' || c == '\u6D60'
        || c == '\u7E58' || c == '\u6D93' || c == '\u6D57';
}

    // ============================================================
    // web_search
    // ============================================================

    private String webSearch(String query) {
        if (query == null || query.trim().length() == 0) return "搜索关键词不能为空";
        query = query.trim();

        String pid = providerId();
        try {
            // 免费内置引擎
            if ("bing".equals(pid)) {
                String r = searchViaBing(query);
                if (r != null) return r;
            }
            if ("duckduckgo".equals(pid)) {
                String r = searchViaDuckDuckGo(query);
                if (r != null) return r;
            }
            // 通用 JSON 请求（智谱 / Tavily / Brave / Serper / SearXNG / 自定义）
            return genericSearch(pid, query);
        } catch (Throwable t) {
            return "搜索失败: " + t.getMessage();
        }
    }

    /** 通用搜索：按 provider 的 URL / method / body / auth / 字段映射发请求并解析 */
    private String genericSearch(String pid, String query) {
        HttpURLConnection conn = null;
        try {
            String method = providerMethod(pid).toUpperCase();
            String apiKey = providerApiKey();
            int count = providerCount();

            // 模板替换
            String urlTpl = providerUrl(pid);
            String url = renderTemplate(urlTpl, query, count, apiKey);
            if (url == null || url.trim().length() == 0) {
                return "搜索失败：提供商 " + SearchProvider.nameOf(pid) + " 未配置 URL";
            }

            String bodyTpl = providerBody(pid);
            String body = renderTemplate(bodyTpl, query, count, apiKey);

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/json, text/*");

            // 鉴权
            String authHeader = providerAuthHeader(pid);
            String authPrefix = providerAuthPrefix(pid);
            if (authHeader.length() > 0 && apiKey.length() > 0) {
                conn.setRequestProperty(authHeader, authPrefix + apiKey);
            }

            // POST：写 body
            if ("POST".equals(method) && body.length() > 0) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is, 2 * 1024 * 1024);

            if (code != 200) {
                return "搜索失败 " + code + "（" + SearchProvider.nameOf(pid) + "）：" + clip(resp, 400);
            }

            // 解析：按 resultPath + 字段映射
            JSONObject root = new JSONObject(resp);
            JSONArray arr = extractArray(root, providerResultPath(pid));
            if (arr == null || arr.length() == 0) {
                return "搜索失败：未从响应中找到结果数组（provider=" + pid
                        + "，resultPath=" + providerResultPath(pid) + "）\n"
                        + "响应片段: " + clip(resp, 500);
            }

            JSONObject o = new JSONObject();
            o.put("provider", pid);
            o.put("query", query);
            JSONArray items = new JSONArray();
int added = 0;
for (int i = 0; i < arr.length() && added < count; i++) {
    JSONObject raw = arr.optJSONObject(i);
    if (raw == null) continue;
    String title = raw.optString(providerTitleKey(pid), "");
    String content = raw.optString(providerContentKey(pid), "");
    // 过滤明显乱码的结果（智谱等有时会返回编码错误的网页）
    if (isLikelyGarbled(title) || isLikelyGarbled(content)) continue;
    JSONObject item = new JSONObject();
    item.put("title", title);
    item.put("content", content);
    item.put("link", raw.optString(providerLinkKey(pid), ""));
    items.put(item);
    added++;
}
            o.put("items", items);
            return SEARCH_PREFIX + o.toString();
        } catch (Throwable t) {
            return "搜索失败: " + t.getMessage();
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    /** 模板占位符替换：{{query}} {{count}} {{api_key}} */
    private String renderTemplate(String tpl, String query, int count, String apiKey) {
        if (tpl == null) return "";
        String out = tpl;
        try {
            String qEnc = URLEncoder.encode(query, "UTF-8").replace("+", "%20");
            out = out.replace("{{query}}", qEnc);
            out = out.replace("{{query_raw}}", query);
            out = out.replace("{{count}}", String.valueOf(count));
            out = out.replace("{{api_key}}", apiKey);
        } catch (Throwable t) {}
        return out;
    }

    /** 按点路径从 JSON 里取数组；路径为空则尝试把 root 本身当数组 */
    private JSONArray extractArray(JSONObject root, String path) {
        try {
            if (path == null || path.length() == 0) {
                // 尝试把 root 当数组？JSONObject 本身不是数组，返回 null
                return null;
            }
            String[] parts = path.split("\\.");
            Object cur = root;
            for (int i = 0; i < parts.length; i++) {
                if (cur instanceof JSONObject) {
                    cur = ((JSONObject) cur).opt(parts[i]);
                } else {
                    return null;
                }
            }
            if (cur instanceof JSONArray) return (JSONArray) cur;
            return null;
        } catch (Throwable t) { return null; }
    }

    // ============================================================
    // Bing RSS（专用解析）
    // ============================================================

    private String searchViaBing(String query) {
        try {
            String url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8")
                    + "&format=rss&count=" + providerCount();
            String xml = httpGet(url, 500000);
            if (xml == null) return "搜索失败：Bing 无响应";
            Pattern pItem = Pattern.compile("(?is)<item>(.*?)</item>");
            Pattern pTitle = Pattern.compile("(?is)<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>");
            Pattern pLink = Pattern.compile("(?is)<link>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</link>");
            Pattern pDesc = Pattern.compile("(?is)<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</description>");
            Matcher mi = pItem.matcher(xml);
            JSONObject o = new JSONObject();
            o.put("provider", "bing");
            o.put("query", query);
            JSONArray arr = new JSONArray();
            int idx = 0;
            while (mi.find() && idx < providerCount()) {
                String item = mi.group(1);
                String title = firstGroup(pTitle, item);
                String link = firstGroup(pLink, item);
                String desc = firstGroup(pDesc, item);
                if (title.length() == 0 && link.length() == 0) continue;
                JSONObject it = new JSONObject();
                it.put("title", stripHtml(title));
                it.put("link", link);
                it.put("content", stripHtml(desc));
                it.put("media", "Bing");
                arr.put(it);
                idx++;
            }
            if (idx == 0) return "搜索失败：Bing 未返回结果";
            o.put("items", arr);
            return SEARCH_PREFIX + o.toString();
        } catch (Throwable t) {
            return "搜索失败: " + t.getMessage();
        }
    }

    // ============================================================
    // DuckDuckGo HTML（专用解析）
    // ============================================================

    private String searchViaDuckDuckGo(String query) {
        try {
            String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
            String html = httpGet(url, 500000);
            if (html == null) return "搜索失败：无响应（可能被网络限制）";

            Pattern pLink = Pattern.compile("(?is)<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
            Pattern pSnip = Pattern.compile("(?is)class=\"result__snippet\"[^>]*>(.*?)</a>");
            Matcher ml = pLink.matcher(html);
            Matcher ms = pSnip.matcher(html);
            JSONObject o = new JSONObject();
            o.put("provider", "duckduckgo");
            o.put("query", query);
            JSONArray arr = new JSONArray();
            int idx = 0;
            while (ml.find() && idx < providerCount()) {
                String href = normalizeDdgLink(ml.group(1));
                String title = stripHtml(ml.group(2)).trim();
                String snippet = "";
                if (ms.find()) snippet = stripHtml(ms.group(1)).trim();
                JSONObject it = new JSONObject();
                it.put("title", title);
                it.put("link", href);
                it.put("content", snippet);
                it.put("media", "DuckDuckGo");
                arr.put(it);
                idx++;
            }
            if (idx == 0) return "未解析到搜索结果（可能是网络受限或搜索被拦截）。可以改用 fetch_url 直接抓取已知网页。";
            o.put("items", arr);
            return SEARCH_PREFIX + o.toString();
        } catch (Throwable t) {
            return "搜索失败: " + t.getMessage();
        }
    }

    /** DuckDuckGo 的跳转链接 //duckduckgo.com/l/?uddg=<编码后的真实地址> */
    private String normalizeDdgLink(String href) {
        try {
            if (href == null) return "";
            if (href.startsWith("//")) href = "https:" + href;
            int k = href.indexOf("uddg=");
            if (k >= 0) {
                String enc = href.substring(k + 5);
                int amp = enc.indexOf('&');
                if (amp >= 0) enc = enc.substring(0, amp);
                return URLDecoder.decode(enc, "UTF-8");
            }
            return href;
        } catch (Throwable t) { return href; }
    }

    private String firstGroup(Pattern p, String s) {
        try {
            Matcher m = p.matcher(s);
            if (m.find()) return m.group(1).trim();
        } catch (Throwable t) {}
        return "";
    }

    // ============================================================
    // fetch_url（网页阅读）
    // ============================================================

    private String fetchUrl(String url) {
        if (url == null || url.trim().length() == 0) return "URL 不能为空";
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;

        // 智谱 reader（仅当 provider=zhipu 时才走，避免误导用户）
        String key = providerApiKey();
        if ("zhipu".equals(providerId()) && key.length() > 0) {
            String r = readerViaZhipu(url, key);
            if (r != null) return r;
        }

        try {
            String body = httpGet(url, MAX_PAGE_BYTES);
            if (body == null) return "抓取失败：服务器返回非 200（或被重定向）";
            String text = stripHtml(body);
            if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT) + "\n...(内容过长，已截断)";
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("title", url);
            o.put("content", text);
            o.put("provider", "native");
            return READER_PREFIX + o.toString();
        } catch (Throwable t) {
            return "抓取失败: " + t.getMessage();
        }
    }

    /** 智谱网页阅读：POST /api/paas/v4/reader */
    private String readerViaZhipu(String url, String key) {
        HttpURLConnection conn = null;
        try {
            String base = prefs().getString("zhipu_base_url", "https://open.bigmodel.cn/api/paas/v4");
            if (base == null || base.trim().length() == 0) base = "https://open.bigmodel.cn/api/paas/v4";
            base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            String api = base + "/reader";

            conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("User-Agent", UA);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            String retFmt = prefs().getString("reader_return_format", "markdown");
            if (retFmt == null || retFmt.trim().length() == 0) retFmt = "markdown";

            JSONObject body = new JSONObject();
            body.put("url", url);
            body.put("return_format", retFmt);
            body.put("retain_images", false);
            body.put("with_images_summary", false);
            body.put("with_links_summary", false);
            body.put("timeout", 20);
            body.put("no_cache", false);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) return null;
            String resp = readAll(conn.getInputStream(), 2 * 1024 * 1024);
            JSONObject j = new JSONObject(resp);
            JSONObject rr = j.optJSONObject("reader_result");
            if (rr == null) return null;

            String content = rr.optString("content", "");
            String title = rr.optString("title", "");
            String description = rr.optString("description", "");
            String realUrl = rr.optString("url", url);
            if (content.length() > MAX_TEXT) content = content.substring(0, MAX_TEXT) + "\n...(内容过长，已截断)";

            JSONObject o = new JSONObject();
            o.put("url", realUrl);
            o.put("title", title.length() == 0 ? realUrl : title);
            o.put("description", description);
            o.put("content", content);
            o.put("provider", "zhipu");
            return READER_PREFIX + o.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    // ============================================================
    // HTTP / HTML 工具
    // ============================================================

    private String httpGet(String url, int maxBytes) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null && loc.length() > 0) {
                    String next = new URL(new URL(url), loc).toString();
                    try { conn.disconnect(); } catch (Throwable t) {}
                    return httpGet(next, maxBytes);
                }
            }
            if (code != 200) return null;
            return readAll(conn.getInputStream(), maxBytes);
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private String readAll(InputStream is, int maxBytes) {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = is.read(buf)) > 0) {
                if (total + n > maxBytes) {
                    bos.write(buf, 0, maxBytes - total);
                    total = maxBytes;
                    break;
                }
                bos.write(buf, 0, n);
                total += n;
            }
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...(已截断)";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        String s = html;
        try {
            s = s.replaceAll("(?is)<script.*?</script>", " ");
            s = s.replaceAll("(?is)<style.*?</style>", " ");
            s = s.replaceAll("(?is)<noscript.*?</noscript>", " ");
            s = s.replaceAll("(?is)<!--.*?-->", " ");
            s = s.replaceAll("(?is)<br\\s*/?>", "\n");
            s = s.replaceAll("(?is)</(p|div|li|tr|h[1-6]|section|article)>", "\n");
            s = s.replaceAll("(?s)<[^>]+>", " ");
            s = unescapeEntities(s);
            s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
            s = s.replaceAll(" *\\n *", "\n");
            s = s.replaceAll("\\n{3,}", "\n\n");
            return s.trim();
        } catch (Throwable t) {
            return s;
        }
    }

    private String unescapeEntities(String s) {
        if (s == null) return "";
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
             .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
             .replace("&apos;", "'").replace("&mdash;", "—").replace("&ndash;", "–")
             .replace("&hellip;", "…").replace("&ldquo;", "“").replace("&rdquo;", "”");
        try {
            Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int cp;
                if ("x".equals(m.group(1))) cp = Integer.parseInt(m.group(2), 16);
                else cp = Integer.parseInt(m.group(2), 10);
                if (cp <= 0 || cp > 0x10FFFF) cp = '?';
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
            }
            m.appendTail(sb);
            s = sb.toString();
        } catch (Throwable t) {}
        return s;
    }

    // ============================================================
    // 给 MainActivity 用：结构化结果转 AI 可读文本
    // ============================================================

    public static String searchResultToAiText(String raw) {
        if (raw == null) return "";
        if (!raw.startsWith(SEARCH_PREFIX)) return raw;
        try {
            JSONObject o = new JSONObject(raw.substring(SEARCH_PREFIX.length()));
            String query = o.optString("query", "");
            String provider = o.optString("provider", "");
            JSONArray arr = o.optJSONArray("items");
            StringBuilder sb = new StringBuilder();
            sb.append("搜索关键词: ").append(query);
            if (provider.length() > 0) sb.append("（引擎: ").append(provider).append("）");
            sb.append("\n\n");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it == null) continue;
                sb.append(i + 1).append(". ").append(it.optString("title", "")).append("\n");
                String link = it.optString("link", "");
                if (link.length() > 0) sb.append(link).append("\n");
                String media = it.optString("media", "");
                String pd = it.optString("publish_date", "");
                if (media.length() > 0 || pd.length() > 0) {
                    sb.append("（").append(media);
                    if (pd.length() > 0) sb.append(" · ").append(pd);
                    sb.append("）\n");
                }
                String c = it.optString("content", "");
                if (c.length() > 0) sb.append(c).append("\n");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Throwable t) { return raw; }
    }

    public static String readerResultToAiText(String raw) {
        if (raw == null) return "";
        if (!raw.startsWith(READER_PREFIX)) return raw;
        try {
            JSONObject o = new JSONObject(raw.substring(READER_PREFIX.length()));
            String title = o.optString("title", "");
            String url = o.optString("url", "");
            String content = o.optString("content", "");
            StringBuilder sb = new StringBuilder();
            if (title.length() > 0) sb.append("标题: ").append(title).append("\n");
            if (url.length() > 0) sb.append("URL: ").append(url).append("\n\n");
            sb.append(content);
            return sb.toString();
        } catch (Throwable t) { return raw; }
    }
}