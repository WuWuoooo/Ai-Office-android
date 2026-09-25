package com.ai.office;

/**
 * 实时通话模型供应商预设。
 *
 * 支持任何兼容 OpenAI Realtime API 协议（或智谱 GLM-Realtime）的服务：
 *   - GLM-Realtime（智谱官方）
 *   - 自定义 URL + 模型名 + 协议（openai / zhipu）
 *
 * 关键配置项：
 *   - URL         WebSocket 地址（wss://...）
 *   - MODEL       模型名
 *   - PROTOCOL    openai=OpenAI Realtime / zhipu=GLM-Realtime（事件名与字段略有差异）
 *   - AUTH_STYLE  header=Authorization 头 / query=?Authorization=
 *   - VOICE       音色（alloy / echo / tongtong 等，视模型而定）
 */
public class RealtimeConfig {

    public static final String[] IDS = {
            "glm-realtime", "openai-realtime", "custom"
    };
    public static final String[] NAMES = {
            "智谱 GLM-Realtime", "OpenAI Realtime", "自定义"
    };
    public static final String[] URLS = {
            "wss://open.bigmodel.cn/api/paas/v4/realtime",
            "wss://api.openai.com/v1/realtime",
            ""
    };
    public static final String[] MODELS = {
            "glm-realtime-flash",
            "gpt-4o-realtime-preview",
            ""
    };
    public static final String[] PROTOCOLS = {
            "zhipu", "openai", "openai"
    };
    /** header=用 Authorization 请求头；query=拼到 URL 上（部分代理要求） */
    public static final String[] AUTH_STYLES = {
            "header", "header", "header"
    };
    /** 音色预设（逗号分隔），用户可在设置里改 */
    public static final String[] VOICES = {
            "tongtong,chuichui,xiaochen,jam,wangjia",
            "alloy,echo,shimmer,verse",
            ""
    };

    public static int indexOf(String id) {
        if (id == null) return IDS.length - 1;
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return IDS.length - 1;
    }

    public static String nameOf(String id) { return NAMES[indexOf(id)]; }

    public static String urlOf(String id) { return URLS[indexOf(id)]; }

    public static String modelOf(String id) { return MODELS[indexOf(id)]; }

    public static String protocolOf(String id) { return PROTOCOLS[indexOf(id)]; }

    public static String authStyleOf(String id) { return AUTH_STYLES[indexOf(id)]; }

    public static String voicesOf(String id) { return VOICES[indexOf(id)]; }

    public static String[] allNames() { return NAMES; }

    /**
     * 从设置里读取实时通话配置。
     * 优先用实时通话自己的配置；缺项回退到主 AI 配置。
     *
     * @return [url, model, apiKey, protocol, voice]
     */
    public static String[] resolve(android.content.Context ctx) {
        try {
            android.content.SharedPreferences p =
                    ctx.getSharedPreferences("ai_office_config", android.content.Context.MODE_PRIVATE);
            String provider = p.getString("realtime_provider", "glm-realtime");

            String url = p.getString("realtime_url", "");
            if (url == null || url.trim().length() == 0) url = urlOf(provider);

            String model = p.getString("realtime_model", "");
            if (model == null || model.trim().length() == 0) model = modelOf(provider);

            String key = p.getString("realtime_api_key", "");
            if (key == null || key.trim().length() == 0) {
                // 回退到主 AI 的 key
                key = p.getString("api_key", "");
            }

            String protocol = p.getString("realtime_protocol", "");
            if (protocol == null || protocol.trim().length() == 0) protocol = protocolOf(provider);

            String voice = p.getString("realtime_voice", "");
            if (voice == null || voice.trim().length() == 0) {
                String voices = voicesOf(provider);
                if (voices != null && voices.length() > 0) {
                    int comma = voices.indexOf(',');
                    voice = comma > 0 ? voices.substring(0, comma) : voices;
                } else {
                    voice = "alloy";
                }
            }

            return new String[]{url == null ? "" : url.trim(),
                                model == null ? "" : model.trim(),
                                key == null ? "" : key.trim(),
                                protocol == null ? "openai" : protocol.trim(),
                                voice == null ? "alloy" : voice.trim()};
        } catch (Throwable t) {
            return new String[]{"", "", "", "openai", "alloy"};
        }
    }
}