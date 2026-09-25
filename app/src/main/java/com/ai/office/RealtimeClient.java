package com.ai.office;

import android.util.Base64;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * GLM-Realtime / OpenAI Realtime 客户端封装。
 *
 * 事件名差异（两种协议基本一致，仅智谱在少数事件名上略有不同）：
 *   客户端发送：
 *     - session.update                     会话配置（模型、音色、系统提示、工具等）
 *     - input_audio_buffer.append           追加音频块（base64 PCM16）
 *     - input_audio_buffer.commit           提交音频
 *     - input_image_buffer.append           追加图片帧（仅智谱，用于视频/共享屏幕）
 *     - conversation.item.create            注入用户消息 / 函数结果
 *     - response.create                     触发 AI 回复
 *     - response.cancel                     打断 AI 回复
 *   服务端接收：
 *     - session.created / session.updated
 *     - response.audio_transcript.delta     音频对应的文本
 *     - response.audio.delta                音频增量（base64 PCM16）
 *     - response.text.delta                 纯文本增量（无音频时）
 *     - response.function_call_arguments.done  函数调用参数
 *     - response.done
 *     - error
 */
public class RealtimeClient {

    public interface Listener {
        void onConnected();
        /** AI 音频增量（base64 编码的 PCM16，24kHz mono） */
        void onAudioDelta(String base64Pcm);
        /** AI 音频对应的文本增量 */
        void onTranscriptDelta(String text);
        /** 纯文本增量（部分模型可能没有音频，只发文本） */
        void onTextDelta(String text);
        /** 用户语音被识别的文本 */
        void onUserTranscript(String text);
        /** 函数调用（参数已完整） */
        void onFunctionCall(String callId, String name, String argsJson);
        /** AI 一轮回复完成 */
        void onResponseDone();
        /** 错误 */
        void onError(String message);
        /** 连接关闭 */
        void onClosed(int code, String reason);
    }

    private final WebSocketClient ws = new WebSocketClient();
    private Listener listener;
    private String protocol = "openai";  // "openai" 或 "zhipu"
    private volatile boolean connected = false;

    public boolean isConnected() { return connected; }

    /**
     * 建立连接。
     *
     * @param url     wss:// 地址
     * @param apiKey  Bearer Key
     * @param authStyle header / query
     * @param protocol openai / zhipu
     */
    public void connect(final String url, final String apiKey, final String authStyle,
                        final String protocol, final Listener l) {
        this.listener = l;
        this.protocol = (protocol == null) ? "openai" : protocol.trim().toLowerCase();

        String realUrl = url;
        Map<String, String> headers = new HashMap<String, String>();

        if ("query".equalsIgnoreCase(authStyle)) {
            // 拼到 URL 上（部分代理要求）
            String sep = realUrl.contains("?") ? "&" : "?";
            realUrl = realUrl + sep + "Authorization=" + apiKey;
        } else {
            headers.put("Authorization", "Bearer " + apiKey);
            // 智谱也接受 api-key 头
            if ("zhipu".equals(this.protocol)) headers.put("api-key", apiKey);
        }

        ws.connect(realUrl, headers, new WebSocketClient.Listener() {
            @Override public void onOpen() {
                connected = true;
                if (listener != null) listener.onConnected();
            }
            @Override public void onMessage(String text) {
                handleEvent(text);
            }
            @Override public void onClose(int code, String reason) {
                connected = false;
                if (listener != null) listener.onClosed(code, reason);
            }
            @Override public void onError(String message) {
                connected = false;
                if (listener != null) listener.onError(message);
            }
        });
    }

    public void close() {
        connected = false;
        ws.close();
    }

    // ============================================================
    // 客户端 → 服务端
    // ============================================================

    /** 更新会话配置：模型、音色、系统提示、工具、VAD 等 */
    public void updateSession(String model, String voice, String instructions, org.json.JSONArray tools) {
        try {
            JSONObject session = new JSONObject();
            session.put("model", model);
            if (voice != null && voice.length() > 0) session.put("voice", voice);
            session.put("input_audio_format", "pcm16");
            session.put("output_audio_format", "pcm16");
            session.put("modalities", new org.json.JSONArray().put("audio").put("text"));

            if (instructions != null && instructions.length() > 0) {
                session.put("instructions", instructions);
            }
            if (tools != null && tools.length() > 0) {
                session.put("tools", tools);
                session.put("tool_choice", "auto");
            }

            // 服务端 VAD：自动检测用户说完就触发 AI 回复
            JSONObject vad = new JSONObject();
            vad.put("type", "server_vad");
            vad.put("threshold", 0.5);
            vad.put("prefix_padding_ms", 300);
            vad.put("silence_duration_ms", 600);
            session.put("turn_detection", vad);

            // 智谱额外字段：实时视频输入（需要时开启）
            if ("zhipu".equals(protocol)) {
                session.put("input_video_format", "jpeg");
            }

            JSONObject evt = new JSONObject();
            evt.put("type", "session.update");
            evt.put("session", session);
            ws.send(evt.toString());
        } catch (Throwable t) {
            notifyError("updateSession 失败: " + t.getMessage());
        }
    }

    /** 追加音频块（base64 编码的 PCM16 数据） */
    public void appendAudio(String base64Pcm) {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", "input_audio_buffer.append");
            evt.put("audio", base64Pcm);
            ws.send(evt.toString());
        } catch (Throwable t) {
            notifyError("appendAudio 失败: " + t.getMessage());
        }
    }

    /** 提交音频（只在不用服务端 VAD 时需要，默认服务端 VAD 会自动提交） */
    public void commitAudio() {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", "input_audio_buffer.commit");
            ws.send(evt.toString());
        } catch (Throwable t) {}
    }

    /** 追加图片帧（base64 编码的 JPEG，用于视频 / 共享屏幕） */
    public void appendImage(String base64Jpeg) {
        try {
            JSONObject evt = new JSONObject();
            if ("zhipu".equals(protocol)) {
                evt.put("type", "input_image_buffer.append");
                evt.put("image", base64Jpeg);
            } else {
                // OpenAI Realtime 走 conversation.item.create
                JSONObject content = new JSONObject();
                content.put("type", "input_image");
                content.put("image_url", "data:image/jpeg;base64," + base64Jpeg);
                JSONObject item = new JSONObject();
                item.put("type", "message");
                item.put("role", "user");
                item.put("content", new org.json.JSONArray().put(content));
                evt.put("type", "conversation.item.create");
                evt.put("item", item);
            }
            ws.send(evt.toString());
        } catch (Throwable t) {}
    }

    /** 注入一条 user 文本消息 */
    public void sendUserText(String text) {
        try {
            JSONObject content = new JSONObject();
            content.put("type", "input_text");
            content.put("text", text == null ? "" : text);

            JSONObject item = new JSONObject();
            item.put("type", "message");
            item.put("role", "user");
            item.put("content", new org.json.JSONArray().put(content));

            JSONObject evt = new JSONObject();
            evt.put("type", "conversation.item.create");
            evt.put("item", item);
            ws.send(evt.toString());

            createResponse();
        } catch (Throwable t) {
            notifyError("sendUserText 失败: " + t.getMessage());
        }
    }

    /** 返回函数调用结果 */
    public void sendFunctionResult(String callId, String resultJson) {
        try {
            JSONObject output = new JSONObject();
            output.put("type", "function_call_output");
            output.put("call_id", callId);
            output.put("output", resultJson == null ? "{}" : resultJson);

            JSONObject evt = new JSONObject();
            evt.put("type", "conversation.item.create");
            evt.put("item", output);
            ws.send(evt.toString());

            createResponse();
        } catch (Throwable t) {
            notifyError("sendFunctionResult 失败: " + t.getMessage());
        }
    }

    /** 触发 AI 生成回复 */
    public void createResponse() {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", "response.create");
            ws.send(evt.toString());
        } catch (Throwable t) {}
    }

    /** 打断当前 AI 回复 */
    public void cancelResponse() {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", "response.cancel");
            ws.send(evt.toString());
        } catch (Throwable t) {}
    }

    // ============================================================
    // 服务端 → 客户端 事件分发
    // ============================================================

    private void handleEvent(String text) {
        if (text == null) return;
        try {
            JSONObject o = new JSONObject(text);
            String type = o.optString("type", "");

            if ("error".equals(type)) {
                String msg = o.optString("message");
                JSONObject err = o.optJSONObject("error");
                if (err != null) {
                    String m2 = err.optString("message");
                    if (m2.length() > 0) msg = m2;
                }
                notifyError(msg.length() == 0 ? "服务端错误" : msg);
                return;
            }

            if ("session.created".equals(type) || "session.updated".equals(type)) {
                // 忽略
                return;
            }

            if ("response.audio.delta".equals(type) || "response.output_audio.delta".equals(type)) {
                String audio = o.optString("delta", "");
                if (audio.length() > 0 && listener != null) listener.onAudioDelta(audio);
                return;
            }

            if ("response.audio_transcript.delta".equals(type)
                    || "response.output_audio_transcript.delta".equals(type)) {
                String t = o.optString("delta", "");
                if (t.length() > 0 && listener != null) listener.onTranscriptDelta(t);
                return;
            }

            if ("response.text.delta".equals(type) || "response.output_text.delta".equals(type)) {
                String t = o.optString("delta", "");
                if (t.length() > 0 && listener != null) listener.onTextDelta(t);
                return;
            }

            if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                String t = o.optString("transcript", "");
                if (t.length() > 0 && listener != null) listener.onUserTranscript(t);
                return;
            }

            if ("response.function_call_arguments.done".equals(type)
                    || "response.output_item.done".equals(type)) {
                JSONObject item = o.optJSONObject("item");
                if (item != null && "function_call".equals(item.optString("type"))) {
                    String callId = item.optString("call_id", item.optString("id", ""));
                    String name = item.optString("name", "");
                    String args = item.optString("arguments", "{}");
                    if (listener != null) listener.onFunctionCall(callId, name, args);
                }
                return;
            }

            if ("response.done".equals(type)) {
                if (listener != null) listener.onResponseDone();
                return;
            }
        } catch (Throwable t) {
            // 忽略无法解析的事件（心跳等）
        }
    }

    private void notifyError(String msg) {
        if (listener != null) {
            try { listener.onError(msg); } catch (Throwable t) {}
        }
    }
}