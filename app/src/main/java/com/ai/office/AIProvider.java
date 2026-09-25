package com.ai.office;

/**
 * 多 AI 供应商预设。
 *
 * 绝大多数预设兼容 OpenAI 的 /chat/completions 协议；
 * Anthropic 使用原生 /v1/messages 协议，由 protocolOf() 返回 "anthropic" 让 AiClient 走专用分支。
 */
public class AIProvider {

    public static final String[] IDS = {
            "deepseek", "openai", "anthropic", "moonshot", "zhipu", "siliconflow", "dashscope", "custom"
    };
    public static final String[] NAMES = {
            "DeepSeek 官方", "OpenAI 官方", "Anthropic Claude", "Moonshot (Kimi)", "智谱 GLM",
            "SiliconFlow", "阿里通义 (Qwen)", "自定义（OpenAI 兼容）"
    };
    public static final String[] URLS = {
            "https://api.deepseek.com",
            "https://api.openai.com/v1",
            "https://api.anthropic.com",
            "https://api.moonshot.cn/v1",
            "https://open.bigmodel.cn/api/paas/v4",
            "https://api.siliconflow.cn/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            ""
    };
    public static final String[] MODEL_PRESETS = {
            "deepseek-flash,deepseek-v4-pro",
            "gpt-6-astra,gpt-6-sol,gpt-6-luna",
            "claude-opus-5-5,claude-sonnet-5,claude-haiku-4-5",
            "kimi-k3,kimi-k2.7-code,kimi-k2.6",
            "glm-5.3,glm-5.3-flash,glm-5.2-fast-preview",
            "deepseek-ai/DeepSeek-V4-Pro,deepseek-ai/DeepSeek-V4-Flash,Qwen/Qwen3.5-122B-A10B,zai-org/GLM-5.3,moonshotai/Kimi-K2.7-Code",
            "qwen3.8-max,qwen3.8-flash,qwen3-max",
            ""
    };
    /** 协议类型：绝大多数走 "openai"，Anthropic 走 "anthropic" */
    public static final String[] PROTOCOLS = {
            "openai", "openai", "anthropic", "openai", "openai",
            "openai", "openai", "openai"
    };

    public static int indexOf(String id) {
        if (id == null) return IDS.length - 1;
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return IDS.length - 1;
    }

    public static String urlOf(String id) { return URLS[indexOf(id)]; }

    public static String nameOf(String id) { return NAMES[indexOf(id)]; }

    public static String modelsOf(String id) { return MODEL_PRESETS[indexOf(id)]; }

    /** 返回该供应商应使用的协议：openai / anthropic */
    public static String protocolOf(String id) { return PROTOCOLS[indexOf(id)]; }

    /** 返回 {名称…} 用于设置页弹窗 */
    public static String[] allNames() { return NAMES; }
}