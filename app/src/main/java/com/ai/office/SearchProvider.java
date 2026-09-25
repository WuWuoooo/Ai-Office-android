package com.ai.office;

/**
 * 联网搜索提供商预设。
 *
 * 每家的差异用这几组平行数组描述：
 *   - IDS / NAMES        提供商标识与显示名
 *   - NEEDS_KEY          是否需要 API Key
 *   - METHODS            请求方法：GET / POST
 *   - URLS               请求地址
 *   - BODY_TEMPLATES     请求体模板（POST 用）；占位符：{{query}} {{count}} {{api_key}}
 *   - AUTH_HEADERS       鉴权头名称（如 "Authorization" 或 "X-Subscription-Token"；空串表示不需要）
 *   - AUTH_PREFIXES      鉴权头的值前缀（如 "Bearer " 或空串）
 *   - RESULT_PATHS       响应 JSON 里结果数组的字段名（如 "search_result" / "results" / "items"）
 *   - TITLE_KEYS         结果条目里标题的字段名
 *   - CONTENT_KEYS       结果条目里摘要的字段名
 *   - LINK_KEYS          结果条目里链接的字段名
 *
 * 特殊值 ""（空串）含义：
 *   - RESULT_PATHS = ""         → 响应体本身就是一个数组
 *   - TITLE_KEYS = "" 等        → 该字段没有，UI 会退化为显示链接
 */
public class SearchProvider {

    public static final String[] IDS = {
            "bing", "duckduckgo", "zhipu", "tavily", "brave", "serper", "searxng", "custom"
    };
    public static final String[] NAMES = {
            "Bing RSS（免费）", "DuckDuckGo（免费）", "智谱 Web Search",
            "Tavily", "Brave Search", "Serper (Google)",
            "SearXNG（自建）", "自定义"
    };
    public static final boolean[] NEEDS_KEY = {
            false, false, true, true, true, true, false, false
    };
    public static final String[] METHODS = {
            "GET", "GET", "POST", "POST", "GET", "POST", "GET", "GET"
    };
    public static final String[] URLS = {
            "https://www.bing.com/search?q={{query}}&format=rss&count={{count}}",
            "https://html.duckduckgo.com/html/?q={{query}}",
            "https://open.bigmodel.cn/api/paas/v4/web_search",
            "https://api.tavily.com/search",
            "https://api.search.brave.com/res/v1/web/search?q={{query}}&count={{count}}",
            "https://google.serper.dev/search",
            "http://localhost:8080/search?q={{query}}&format=json",
            ""
    };
    public static final String[] BODY_TEMPLATES = {
            "",
            "",
            "{\"search_query\":\"{{query}}\",\"search_engine\":\"search_std\",\"count\":{{count}}}",
            "{\"api_key\":\"{{api_key}}\",\"query\":\"{{query}}\",\"max_results\":{{count}}}",
            "",
            "{\"q\":\"{{query}}\",\"num\":{{count}}}",
            "",
            ""
    };
    public static final String[] AUTH_HEADERS = {
            "", "", "Authorization", "Authorization", "X-Subscription-Token", "X-API-KEY", "", ""
    };
    public static final String[] AUTH_PREFIXES = {
            "", "", "Bearer ", "Bearer ", "", "", "", ""
    };
    public static final String[] RESULT_PATHS = {
            "rss", "html", "search_result", "results", "web.results", "organic", "", "results"
    };
    public static final String[] TITLE_KEYS = {
            "title", "title", "title", "title", "title", "title", "title", "title"
    };
    public static final String[] CONTENT_KEYS = {
            "description", "snippet", "content", "content", "description", "snippet", "content", "content"
    };
    public static final String[] LINK_KEYS = {
            "link", "link", "link", "url", "url", "link", "url", "url"
    };

    public static int indexOf(String id) {
        if (id == null) return 0;
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return 0;
    }

    public static String nameOf(String id) { return NAMES[indexOf(id)]; }

    public static boolean needsKey(String id) { return NEEDS_KEY[indexOf(id)]; }

    public static String methodOf(String id) { return METHODS[indexOf(id)]; }

    public static String urlOf(String id) { return URLS[indexOf(id)]; }

    public static String bodyOf(String id) { return BODY_TEMPLATES[indexOf(id)]; }

    public static String authHeaderOf(String id) { return AUTH_HEADERS[indexOf(id)]; }

    public static String authPrefixOf(String id) { return AUTH_PREFIXES[indexOf(id)]; }

    public static String resultPathOf(String id) { return RESULT_PATHS[indexOf(id)]; }

    public static String titleKeyOf(String id) { return TITLE_KEYS[indexOf(id)]; }

    public static String contentKeyOf(String id) { return CONTENT_KEYS[indexOf(id)]; }

    public static String linkKeyOf(String id) { return LINK_KEYS[indexOf(id)]; }

    /** 用于设置页弹窗 */
    public static String[] allNames() { return NAMES; }
}