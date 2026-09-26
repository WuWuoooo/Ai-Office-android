package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 文生图模型供应商预设。
 * 绝大多数兼容 OpenAI 的 /images/generations 协议；
 * 返回可能是 url 或 b64_json，ImageGenClient 会自动处理两种。
 */
public class ImageGenConfig {

    public static final String[] IDS = {
            "zhipu", "openai", "siliconflow", "custom"
    };
    public static final String[] NAMES = {
            "智谱 CogView", "OpenAI DALL-E", "SiliconFlow Kolors", "自定义（OpenAI 兼容）"
    };
    public static final String[] URLS = {
            "https://open.bigmodel.cn/api/paas/v4",
            "https://api.openai.com/v1",
            "https://api.siliconflow.cn/v1",
            ""
    };
    public static final String[] MODELS = {
            "cogview-4",
            "dall-e-3",
            "Kwai-Kolors/Kolors",
            ""
    };
    public static final String[] SIZES = {
            "1024x1024,768x1344,864x1152,1344x768,1152x864,1440x720,720x1440",
            "1024x1024,1792x1024,1024x1792",
            "1024x1024,768x1024,720x1440,1440x720",
            "1024x1024"
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

    public static String[] sizesOf(String id) {
        String csv = SIZES[indexOf(id)];
        if (csv == null || csv.length() == 0) return new String[]{"1024x1024"};
        String[] arr = csv.split(",");
        for (int i = 0; i < arr.length; i++) arr[i] = arr[i].trim();
        return arr;
    }

    public static String[] allNames() { return NAMES; }

    /**
     * 从设置里读取文生图配置。
     * 优先用文生图自己的配置；缺项回退到预设；Key 缺则回退到主 API Key。
     *
     * @return [baseUrl, apiKey, model, size]
     */
    public static String[] resolve(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
            String provider = p.getString("image_gen_provider", "zhipu");

            String base = p.getString("image_gen_base_url", "");
            if (base == null || base.trim().length() == 0) base = urlOf(provider);

            String key = p.getString("image_gen_api_key", "");
            if (key == null || key.trim().length() == 0) {
                key = p.getString("api_key", "");
            }

            String model = p.getString("image_gen_model", "");
            if (model == null || model.trim().length() == 0) model = modelOf(provider);

            String size = p.getString("image_gen_size", "");
            if (size == null || size.trim().length() == 0) {
                String[] ss = sizesOf(provider);
                size = (ss.length > 0) ? ss[0] : "1024x1024";
            }

            return new String[]{
                    base == null ? "" : base.trim(),
                    key == null ? "" : key.trim(),
                    model == null ? "" : model.trim(),
                    size == null ? "1024x1024" : size.trim()
            };
        } catch (Throwable t) {
            return new String[]{"", "", "", "1024x1024"};
        }
    }
}