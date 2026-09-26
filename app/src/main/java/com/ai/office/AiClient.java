package com.ai.office;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 客户端，同时支持 OpenAI 兼容协议与 Anthropic 原生 /v1/messages 协议。
 * 支持：流式 reasoning/content/tool_calls、自动重试、思考深度（含关闭）、识图模型独立配置。
 */
public class AiClient {

    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String model;
    private volatile String protocol = "openai";
    private volatile String visionModel = "";

    // 识图模型的独立配置（留空 = 复用主配置）
    private volatile String visionBaseUrl = "";
    private volatile String visionApiKey = "";
    private volatile String visionProtocol = "";

    private volatile boolean isCancelled = false;
    private volatile HttpURLConnection currentConnection = null;
    private volatile boolean running = false;

    public AiClient(String baseUrl, String apiKey, String model) {
        setBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = (baseUrl == null || baseUrl.length() == 0) ? ""
                : (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    }

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public void setModel(String model) { this.model = model; }

    public String getModel() { return model; }

    public void setProtocol(String p) {
        this.protocol = (p == null) ? "openai" : p.trim().toLowerCase();
    }

    public String getProtocol() { return protocol; }

    public void setVisionModel(String m) { this.visionModel = (m == null) ? "" : m.trim(); }

    public String getVisionModel() { return visionModel; }

    /** 设置识图模型的独立配置；三个参数都可以传空串表示复用主配置 */
    public void setVisionEndpoint(String baseUrl, String apiKey, String protocol) {
        try {
            if (baseUrl == null) baseUrl = "";
            baseUrl = baseUrl.trim();
            if (baseUrl.length() > 0 && !baseUrl.endsWith("/")) baseUrl = baseUrl + "/";
            this.visionBaseUrl = baseUrl;
            this.visionApiKey = (apiKey == null) ? "" : apiKey.trim();
            this.visionProtocol = (protocol == null) ? "" : protocol.trim().toLowerCase();
        } catch (Throwable t) {}
    }

    /**
     * 根据消息内容选择请求要使用的 (baseUrl, apiKey, protocol)。
     * 含图片且配置了识图模型 → 用识图配置（缺项回退主配置）；否则用主配置。
     */
    private String[] pickEndpoint(JSONArray messages) {
        try {
            boolean hasImg = containsImage(messages);
            if (hasImg && (visionModel != null && visionModel.length() > 0)) {
                String b = (visionBaseUrl != null && visionBaseUrl.length() > 0) ? visionBaseUrl : baseUrl;
                String k = (visionApiKey != null && visionApiKey.length() > 0) ? visionApiKey : apiKey;
                String p = (visionProtocol != null && visionProtocol.length() > 0) ? visionProtocol : protocol;
                return new String[]{b == null ? "" : b, k == null ? "" : k, p == null ? "openai" : p};
            }
        } catch (Throwable t) {}
        return new String[]{baseUrl == null ? "" : baseUrl, apiKey == null ? "" : apiKey,
                            protocol == null ? "openai" : protocol};
    }

    public boolean isRunning() { return running; }

    public void cancel() {
        isCancelled = true;
        HttpURLConnection c = currentConnection;
        if (c != null) {
            try { c.disconnect(); } catch (Throwable t) {}
            currentConnection = null;
        }
    }

    public interface StreamCallback {
        void onReasoning(String reasoning);
        void onContent(String content);
        void onToolCall(JSONArray toolCalls);
        void onTokenStats(String stats);
        void onRetry(int attempt);
        void onError(String error);
        void onComplete();
    }

    public interface OnceCallback {
        void onResult(String text);
        void onError(String message);
    }

    public void chatStream(final JSONArray messages, final JSONArray tools, final int retryTimes,
                           final String reasoningEffort, final StreamCallback callback) {
        isCancelled = false;
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int attempt = 0;
                while (!isCancelled) {
                    final boolean[] gotOutput = new boolean[1];
                    boolean terminated = doStream(messages, tools, reasoningEffort, callback, gotOutput);
                    if (terminated || isCancelled) break;
                    if (gotOutput[0]) break;
                    if (attempt >= retryTimes) break;
                    attempt++;
                    try { callback.onRetry(attempt); } catch (Throwable t) {}
                    try { Thread.sleep(1000L * attempt); } catch (Throwable t) {}
                }
                running = false;
            }
        }).start();
    }

    private boolean doStream(JSONArray messages, JSONArray tools, String reasoningEffort,
                             StreamCallback callback, boolean[] gotOutput) {
        String[] ep = pickEndpoint(messages);
        if ("anthropic".equals(ep[2])) {
            return doStreamAnthropic(ep[0], ep[1], messages, tools, reasoningEffort, callback, gotOutput);
        }
        return doStreamOpenAI(ep[0], ep[1], messages, tools, reasoningEffort, callback, gotOutput);
    }

    private boolean doStreamOpenAI(String baseUrl, String apiKey, JSONArray messages, JSONArray tools,
                                   String reasoningEffort, StreamCallback callback, boolean[] gotOutput) {
        HttpURLConnection conn = null;
        try {
            String urlStr = (baseUrl == null ? "" : baseUrl) + "chat/completions";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            currentConnection = conn;
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(180000);

            JSONObject body = new JSONObject();
            body.put("model", pickModel(messages));
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("stream", true);

            JSONObject streamOptions = new JSONObject();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);

            String effort = normalizeEffort(reasoningEffort);
            if (effort.length() > 0) body.put("reasoning_effort", effort);

            if (tools != null && tools.length() > 0) {
                body.put("tools", tools);
                body.put("tool_choice", "auto");
            }

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            if (isCancelled) return true;

            int code = conn.getResponseCode();
            if (code != 200) {
                String errText = readErrorStream(conn);
                boolean retryable = code >= 500 || code == 408 || code == 429;
                if (!isCancelled && !retryable) callback.onError("API 错误 " + code + ": " + errText);
                return !retryable;
            }

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            Map<Integer, JSONObject> toolCallMap = new HashMap<Integer, JSONObject>();
            JSONObject usageObject = null;

            while ((line = reader.readLine()) != null) {
                if (isCancelled) break;
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.length() == 0) continue;
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObject chunk = new JSONObject(data);
                    if (chunk.has("usage") && !chunk.isNull("usage")) usageObject = chunk.optJSONObject("usage");
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.optJSONObject(0);
                    if (choice == null) continue;
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta == null) continue;

                    String r = safeStr(delta, "reasoning_content");
                    if (r != null && r.length() > 0) { gotOutput[0] = true; callback.onReasoning(r); }
                    String c = safeStr(delta, "content");
                    if (c != null && c.length() > 0) { gotOutput[0] = true; callback.onContent(c); }

                    JSONArray tcs = delta.optJSONArray("tool_calls");
                    if (tcs != null) {
                        for (int i = 0; i < tcs.length(); i++) {
                            JSONObject tc = tcs.optJSONObject(i);
                            if (tc == null) continue;
                            int index = tc.optInt("index", i);
                            JSONObject existing = toolCallMap.get(Integer.valueOf(index));
                            if (existing == null) {
                                existing = new JSONObject();
                                existing.put("type", "function");
                                JSONObject func = new JSONObject();
                                func.put("name", "");
                                func.put("arguments", "");
                                existing.put("function", func);
                                toolCallMap.put(Integer.valueOf(index), existing);
                            }
                            String id = safeStr(tc, "id");
                            if (id != null && id.length() > 0) existing.put("id", id);
                            JSONObject func = tc.optJSONObject("function");
                            if (func != null) {
                                JSONObject extFunc = existing.getJSONObject("function");
                                String nm = safeStr(func, "name");
                                if (nm != null && nm.length() > 0) extFunc.put("name", nm);
                                String args = safeStr(func, "arguments");
                                if (args != null) extFunc.put("arguments", extFunc.getString("arguments") + args);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (isCancelled) return true;

            if (usageObject != null) {
                try {
                    int promptTokens = usageObject.optInt("prompt_tokens", 0);
                    int completionTokens = usageObject.optInt("completion_tokens", 0);
                    int cacheHit = usageObject.optInt("prompt_cache_hit_tokens", 0);
                    callback.onTokenStats(formatStats(promptTokens, completionTokens, cacheHit));
                } catch (Throwable t) {}
            }

            if (!toolCallMap.isEmpty()) {
                JSONArray finalToolCalls = new JSONArray();
                List<Integer> keys = new ArrayList<Integer>(toolCallMap.keySet());
                Collections.sort(keys);
                for (int i = 0; i < keys.size(); i++) {
                    JSONObject tc = toolCallMap.get(keys.get(i));
                    if (tc == null) continue;
                    String tid = safeStr(tc, "id");
                    if (tid == null || tid.length() == 0) tc.put("id", "call_" + System.currentTimeMillis() + "_" + i);
                    finalToolCalls.put(tc);
                }
                if (finalToolCalls.length() > 0) {
                    callback.onToolCall(finalToolCalls);
                    return true;
                }
            }
            callback.onComplete();
            return true;
        } catch (Throwable e) {
            return isCancelled;
        } finally {
            if (conn != null) { try { conn.disconnect(); } catch (Throwable t) {} }
            if (currentConnection == conn) currentConnection = null;
        }
    }

    private boolean doStreamAnthropic(String baseUrl, String apiKey, JSONArray messages, JSONArray tools,
                                      String reasoningEffort, StreamCallback callback, boolean[] gotOutput) {
        HttpURLConnection conn = null;
        try {
            String urlStr = (baseUrl == null ? "" : baseUrl) + "v1/messages";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            currentConnection = conn;
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(180000);

            JSONObject conv = convertToAnthropic(messages, tools, reasoningEffort);
            if (conv == null) { callback.onError("Anthropic 请求构造失败"); return true; }

            OutputStream os = conn.getOutputStream();
            os.write(conv.toString().getBytes("UTF-8"));
            os.close();

            if (isCancelled) return true;

            int code = conn.getResponseCode();
            if (code != 200) {
                String errText = readErrorStream(conn);
                boolean retryable = code >= 500 || code == 408 || code == 429;
                if (!isCancelled && !retryable) callback.onError("Anthropic 错误 " + code + ": " + errText);
                return !retryable;
            }

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            Map<Integer, JSONObject> blockByIndex = new HashMap<Integer, JSONObject>();
            Map<Integer, StringBuilder> toolArgsByIndex = new HashMap<Integer, StringBuilder>();
            List<Integer> toolIndexOrder = new ArrayList<Integer>();
            int inputTokens = 0, outputTokens = 0, cacheHit = 0;

            while ((line = reader.readLine()) != null) {
                if (isCancelled) break;
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.length() == 0) continue;
                try {
                    JSONObject evt = new JSONObject(data);
                    String type = evt.optString("type", "");

                    if ("message_start".equals(type)) {
                        JSONObject msg = evt.optJSONObject("message");
                        if (msg != null) {
                            JSONObject u = msg.optJSONObject("usage");
                            if (u != null) {
                                inputTokens = u.optInt("input_tokens", 0);
                                cacheHit = u.optInt("cache_read_input_tokens", 0);
                            }
                        }
                    } else if ("content_block_start".equals(type)) {
                        int idx = evt.optInt("index", 0);
                        JSONObject block = evt.optJSONObject("content_block");
                        if (block == null) continue;
                        String bt = block.optString("type", "");
                        blockByIndex.put(Integer.valueOf(idx), block);
                        if ("tool_use".equals(bt)) {
                            toolArgsByIndex.put(Integer.valueOf(idx), new StringBuilder());
                            toolIndexOrder.add(Integer.valueOf(idx));
                        }
                    } else if ("content_block_delta".equals(type)) {
                        int idx = evt.optInt("index", 0);
                        JSONObject delta = evt.optJSONObject("delta");
                        if (delta == null) continue;
                        String dt = delta.optString("type", "");
                        if ("text_delta".equals(dt)) {
                            String t = delta.optString("text", "");
                            if (t.length() > 0) { gotOutput[0] = true; callback.onContent(t); }
                        } else if ("thinking_delta".equals(dt)) {
                            String t = delta.optString("thinking", "");
                            if (t.length() > 0) { gotOutput[0] = true; callback.onReasoning(t); }
                        } else if ("input_json_delta".equals(dt)) {
                            StringBuilder sb = toolArgsByIndex.get(Integer.valueOf(idx));
                            if (sb != null) sb.append(delta.optString("partial_json", ""));
                        }
                    } else if ("message_delta".equals(type)) {
                        JSONObject u = evt.optJSONObject("usage");
                        if (u != null) outputTokens = u.optInt("output_tokens", outputTokens);
                    } else if ("error".equals(type)) {
                        String m = evt.optString("message", "未知错误");
                        callback.onError("Anthropic: " + m);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            if (isCancelled) return true;

            if (inputTokens > 0 || outputTokens > 0) {
                callback.onTokenStats(formatStats(inputTokens, outputTokens, cacheHit));
            }

            if (!toolIndexOrder.isEmpty()) {
                JSONArray finalToolCalls = new JSONArray();
                for (int k = 0; k < toolIndexOrder.size(); k++) {
                    Integer idx = toolIndexOrder.get(k);
                    JSONObject block = blockByIndex.get(idx);
                    StringBuilder argsSb = toolArgsByIndex.get(idx);
                    if (block == null) continue;
                    String id = block.optString("id", "");
                    String name = block.optString("name", "");
                    String args = argsSb == null ? "{}" : argsSb.toString();
                    if (args.length() == 0) args = "{}";
                    JSONObject tc = new JSONObject();
                    tc.put("id", id.length() == 0 ? ("call_" + System.currentTimeMillis() + "_" + k) : id);
                    tc.put("type", "function");
                    JSONObject fn = new JSONObject();
                    fn.put("name", name);
                    fn.put("arguments", args);
                    tc.put("function", fn);
                    finalToolCalls.put(tc);
                }
                if (finalToolCalls.length() > 0) {
                    callback.onToolCall(finalToolCalls);
                    return true;
                }
            }
            callback.onComplete();
            return true;
        } catch (Throwable e) {
            return isCancelled;
        } finally {
            if (conn != null) { try { conn.disconnect(); } catch (Throwable t) {} }
            if (currentConnection == conn) currentConnection = null;
        }
    }

    /** 把 OpenAI 消息格式转成 Anthropic /v1/messages 请求体 */
    private JSONObject convertToAnthropic(JSONArray messages, JSONArray tools, String reasoningEffort) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", pickModel(messages));
            body.put("max_tokens", 8192);
            body.put("stream", true);

            StringBuilder sysSb = new StringBuilder();
            JSONArray anthropicMsgs = new JSONArray();
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = UiUtils.optStr(m, "role");
                if ("system".equals(role)) {
                    if (sysSb.length() > 0) sysSb.append("\n");
                    sysSb.append(UiUtils.contentToText(m, false));
                    continue;
                }
                if ("tool".equals(role)) {
                    JSONObject um = new JSONObject();
                    um.put("role", "user");
                    JSONArray parts = new JSONArray();
                    JSONObject tr = new JSONObject();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", UiUtils.optStr(m, "tool_call_id"));
                    tr.put("content", UiUtils.optStr(m, "content"));
                    parts.put(tr);
                    um.put("content", parts);
                    anthropicMsgs.put(um);
                    continue;
                }
                if ("assistant".equals(role)) {
                    JSONObject am = new JSONObject();
                    am.put("role", "assistant");
                    JSONArray parts = new JSONArray();
                    String content = UiUtils.contentToText(m, true);
                    if (content.length() > 0) {
                        JSONObject txt = new JSONObject();
                        txt.put("type", "text");
                        txt.put("text", content);
                        parts.put(txt);
                    }
                    JSONArray tcs = m.optJSONArray("tool_calls");
                    if (tcs != null) for (int k = 0; k < tcs.length(); k++) {
                        JSONObject tc = tcs.optJSONObject(k);
                        if (tc == null) continue;
                        JSONObject fn = tc.optJSONObject("function");
                        if (fn == null) continue;
                        JSONObject tu = new JSONObject();
                        tu.put("type", "tool_use");
                        tu.put("id", UiUtils.optStr(tc, "id"));
                        tu.put("name", UiUtils.optStr(fn, "name"));
                        try {
                            tu.put("input", new JSONObject(UiUtils.optStr(fn, "arguments")));
                        } catch (Throwable t) {
                            tu.put("input", new JSONObject());
                        }
                        parts.put(tu);
                    }
                    if (parts.length() == 0) {
                        JSONObject txt = new JSONObject();
                        txt.put("type", "text");
                        txt.put("text", " ");
                        parts.put(txt);
                    }
                    am.put("content", parts);
                    anthropicMsgs.put(am);
                    continue;
                }
                JSONObject um = new JSONObject();
                um.put("role", "user");
                Object c = m.opt("content");
                if (c instanceof JSONArray) {
                    JSONArray parts = new JSONArray();
                    JSONArray src = (JSONArray) c;
                    for (int k = 0; k < src.length(); k++) {
                        JSONObject p = src.optJSONObject(k);
                        if (p == null) continue;
                        String pt = p.optString("type", "");
                        if ("text".equals(pt)) {
                            JSONObject txt = new JSONObject();
                            txt.put("type", "text");
                            txt.put("text", p.optString("text", ""));
                            parts.put(txt);
                        } else if ("image_url".equals(pt)) {
                            JSONObject iu = p.optJSONObject("image_url");
                            if (iu == null) continue;
                            String url = iu.optString("url", "");
                            int b = url.indexOf("base64,");
                            if (b < 0) continue;
                            String b64 = url.substring(b + 7);
                            String media = "image/jpeg";
                            if (url.startsWith("data:image/png")) media = "image/png";
                            else if (url.startsWith("data:image/webp")) media = "image/webp";
                            else if (url.startsWith("data:image/gif")) media = "image/gif";
                            JSONObject img = new JSONObject();
                            img.put("type", "image");
                            JSONObject srcObj = new JSONObject();
                            srcObj.put("type", "base64");
                            srcObj.put("media_type", media);
                            srcObj.put("data", b64);
                            img.put("source", srcObj);
                            parts.put(img);
                        }
                    }
                    um.put("content", parts);
                } else {
                    um.put("content", c == null ? "" : String.valueOf(c));
                }
                anthropicMsgs.put(um);
            }

            if (sysSb.length() > 0) body.put("system", sysSb.toString());
            body.put("messages", anthropicMsgs);

            String effort = normalizeEffort(reasoningEffort);
            if (effort.length() > 0) {
                JSONObject thinking = new JSONObject();
                thinking.put("type", "enabled");
                int budget = 4000;
                if ("low".equals(effort)) budget = 1500;
                else if ("high".equals(effort)) budget = 8000;
                else if ("max".equals(effort)) budget = 16000;
                thinking.put("budget_tokens", budget);
                body.put("thinking", thinking);
                body.put("max_tokens", Math.max(8192, budget + 4096));
            }

            if (tools != null && tools.length() > 0) {
                JSONArray at = new JSONArray();
                for (int i = 0; i < tools.length(); i++) {
                    JSONObject t = tools.optJSONObject(i);
                    if (t == null) continue;
                    JSONObject fn = t.optJSONObject("function");
                    if (fn == null) continue;
                    JSONObject nt = new JSONObject();
                    nt.put("name", UiUtils.optStr(fn, "name"));
                    nt.put("description", UiUtils.optStr(fn, "description"));
                    JSONObject params = fn.optJSONObject("parameters");
                    nt.put("input_schema", params == null ? new JSONObject() : params);
                    at.put(nt);
                }
                if (at.length() > 0) {
                    body.put("tools", at);
                    JSONObject tc = new JSONObject();
                    tc.put("type", "auto");
                    body.put("tool_choice", tc);
                }
            }
            return body;
        } catch (Throwable t) { return null; }
    }

    public void chatOnce(final JSONArray messages, final OnceCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] ep = pickEndpoint(messages);
                if ("anthropic".equals(ep[2])) {
                    chatOnceAnthropic(ep[0], ep[1], messages, callback);
                } else {
                    chatOnceOpenAI(ep[0], ep[1], messages, callback);
                }
            }
        }).start();
    }

    private void chatOnceOpenAI(String baseUrl, String apiKey, JSONArray messages, OnceCallback callback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL((baseUrl == null ? "" : baseUrl) + "chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);

            JSONObject body = new JSONObject();
            body.put("model", pickModel(messages));
            body.put("messages", messages);
            body.put("temperature", 0.2);
            body.put("stream", false);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) { callback.onError("摘要请求失败 " + code); return; }
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();

            JSONObject resp = new JSONObject(sb.toString());
            JSONArray choices = resp.optJSONArray("choices");
            if (choices == null || choices.length() == 0) { callback.onError("摘要返回为空"); return; }
            JSONObject msg = choices.optJSONObject(0).optJSONObject("message");
            String text = msg == null ? "" : safeStr(msg, "content");
            if (text == null || text.trim().length() == 0) { callback.onError("摘要返回为空"); return; }
            callback.onResult(text);
        } catch (Throwable e) {
            callback.onError(String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) { try { conn.disconnect(); } catch (Throwable t) {} }
        }
    }

    private void chatOnceAnthropic(String baseUrl, String apiKey, JSONArray messages, OnceCallback callback) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = convertToAnthropic(messages, null, "");
            if (body == null) { callback.onError("Anthropic 请求构造失败"); return; }
            body.put("stream", false);
            body.remove("stream_options");

            URL url = new URL((baseUrl == null ? "" : baseUrl) + "v1/messages");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) { callback.onError("Anthropic 摘要失败 " + code); return; }
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();

            JSONObject resp = new JSONObject(sb.toString());
            JSONArray content = resp.optJSONArray("content");
            if (content == null || content.length() == 0) { callback.onError("摘要返回为空"); return; }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject p = content.optJSONObject(i);
                if (p == null) continue;
                if ("text".equals(p.optString("type"))) {
                    text.append(p.optString("text", ""));
                }
            }
            if (text.length() == 0) { callback.onError("摘要返回为空"); return; }
            callback.onResult(text.toString());
        } catch (Throwable e) {
            callback.onError(String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) { try { conn.disconnect(); } catch (Throwable t) {} }
        }
    }

    private String pickModel(JSONArray messages) {
        if (visionModel != null && visionModel.length() > 0 && containsImage(messages)) {
            return visionModel;
        }
        return model;
    }

    private boolean containsImage(JSONArray messages) {
        try {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                Object c = m.opt("content");
                if (!(c instanceof JSONArray)) continue;
                JSONArray arr = (JSONArray) c;
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject p = arr.optJSONObject(k);
                    if (p != null && "image_url".equals(p.optString("type"))) return true;
                }
            }
        } catch (Throwable t) {}
        return false;
    }

    private static String normalizeEffort(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.length() == 0) return "";
        if ("none".equals(s) || "off".equals(s) || "disable".equals(s) || "disabled".equals(s)) return "";
        return v.trim();
    }

    private static String formatStats(int prompt, int completion, int cacheHit) {
        String stats = "输入: " + prompt + " | 输出: " + completion;
        if (prompt > 0) {
            float rate = (float) cacheHit / (float) prompt * 100f;
            stats += " | 缓存命中: " + String.format("%.1f", Float.valueOf(rate)) + "%";
        }
        return stats;
    }

    private static String readErrorStream(HttpURLConnection conn) {
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) return "";
            BufferedReader er = new BufferedReader(new InputStreamReader(es, "UTF-8"));
            StringBuilder esb = new StringBuilder();
            String el;
            while ((el = er.readLine()) != null) esb.append(el);
            er.close();
            return esb.toString();
        } catch (Throwable t) { return ""; }
    }

    private static String safeStr(JSONObject o, String key) {
        if (o == null || key == null) return null;
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof String) return (String) v;
        return String.valueOf(v);
    }

    // ============================================================
    // 工具定义辅助
    // ============================================================

    private static JSONObject func(String name, String desc, JSONObject props, JSONArray required) throws Exception {
        JSONObject f = new JSONObject();
        f.put("type", "function");
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", desc);
        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", props);
        if (required != null && required.length() > 0) params.put("required", required);
        fn.put("parameters", params);
        f.put("function", fn);
        return f;
    }

    private static JSONObject prop(String type, String desc) throws Exception {
        JSONObject p = new JSONObject();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }

    private static JSONObject propBool(String desc) throws Exception {
        JSONObject p = new JSONObject();
        p.put("type", "boolean");
        p.put("description", desc);
        return p;
    }

    private static JSONObject propInt(String desc) throws Exception {
        JSONObject p = new JSONObject();
        p.put("type", "integer");
        p.put("description", desc);
        return p;
    }

    // ============================================================
    // 工具列表
    // ============================================================

public static JSONArray buildTools() { return buildTools(false, false, false); }

/**
 * 构建工具列表。
 * @param allowShellTool 是否注册 run_shell_command（需用户在设置里开启）
 * @param allowA11yTool  是否注册 accessibility_control（需用户在设置里开启 + 系统无障碍服务）
 */
public static JSONArray buildTools(boolean allowShellTool, boolean allowA11yTool) {
    return buildTools(allowShellTool, allowA11yTool, false);
}

/**
 * 构建工具列表（含文生图）。
 * @param allowImageGen  是否注册 generate_image
 */
public static JSONArray buildTools(boolean allowShellTool, boolean allowA11yTool, boolean allowImageGen) {
        JSONArray tools = new JSONArray();
        try {
            // ---------- 文件：查看 ----------

            JSONObject pList = new JSONObject();
            pList.put("path", prop("string", "要列出的目录路径"));
            tools.put(func("list_files",
                    "列出目录下的一层内容（文件和子目录）。想看整个目录树请用 list_dir_tree。",
                    pList, new JSONArray().put("path")));

            JSONObject pTree = new JSONObject();
            pTree.put("path", prop("string", "要列成树的根目录路径"));
            pTree.put("max_depth", propInt("最大深度（默认 3，最深 8）"));
            pTree.put("max_entries", propInt("最多返回多少条目（默认 300）"));
            tools.put(func("list_dir_tree",
                    "把目录列成树形结构（缩进形式），便于快速了解项目结构。适合「这个目录里有什么」这类问题。",
                    pTree, new JSONArray().put("path")));

            JSONObject pInfo = new JSONObject();
            pInfo.put("path", prop("string", "文件或目录路径"));
            tools.put(func("file_info",
                    "获取文件/目录属性：大小、修改时间、是否目录、子项数量。",
                    pInfo, new JSONArray().put("path")));

            // ---------- 文件：读取 ----------

            JSONObject pRead = new JSONObject();
            pRead.put("path", prop("string", "要读取的文件路径"));
            tools.put(func("read_file",
                    "读取整个文件。大文件会被截断，看大文件某一段请用 read_file_range。",
                    pRead, new JSONArray().put("path")));

            JSONObject pRange = new JSONObject();
            pRange.put("path", prop("string", "文件路径"));
            pRange.put("start_line", propInt("起始行号（从 1 开始）"));
            pRange.put("end_line", propInt("结束行号（含）"));
            tools.put(func("read_file_range",
                    "按行号区间读取文件片段，返回行号 + 内容。查看大文件某一段时用这个，比 read_file 更省。",
                    pRange, new JSONArray().put("path").put("start_line").put("end_line")));

            // ---------- 文件：写入 ----------

            JSONObject pWrite = new JSONObject();
            pWrite.put("path", prop("string", "文件路径"));
            pWrite.put("content", prop("string", "要写入的完整内容"));
            tools.put(func("write_file",
                    "创建新文件或覆写已有文件。覆写会替换全部内容，改已有文件的一小部分请优先用 edit_file。",
                    pWrite, new JSONArray().put("path").put("content")));

            JSONObject pAppend = new JSONObject();
            pAppend.put("path", prop("string", "文件路径"));
            pAppend.put("content", prop("string", "要追加的内容"));
            tools.put(func("append_file", "向已有文件末尾追加内容（不会覆盖原有内容）。",
                    pAppend, new JSONArray().put("path").put("content")));

            JSONObject pEdit = new JSONObject();
            pEdit.put("path", prop("string", "文件路径"));
            pEdit.put("old_string", prop("string", "要被替换掉的原文，必须与文件中已有内容逐字符一致（含缩进与换行）"));
            pEdit.put("new_string", prop("string", "替换成的新内容"));
            pEdit.put("replace_all", propBool("是否替换所有匹配（默认 false，只替换唯一匹配的那一处）"));
            tools.put(func("edit_file",
                    "精确修改文件：把 old_string 替换为 new_string，其余内容保持不变。改已有文件的局部内容时优先使用。"
                  + "old_string 必须来自当前文件的最新真实内容（包括缩进、空格、换行、全角/半角标点都要逐字符一致）。"
                  + "如果返回「未找到要替换的内容」，不要重复尝试同样的 old_string，先用 read_file_range 读取目标区域，再从返回结果中复制。"
                  + "注意：对同一文件连续编辑时，前面的编辑会改变行号和内容，请基于最新内容构造 old_string。",
                    pEdit, new JSONArray().put("path").put("old_string").put("new_string")));

            // ---------- 文件：管理 ----------

            JSONObject pMkdir = new JSONObject();
            pMkdir.put("path", prop("string", "要创建的目录路径（可含多级）"));
            tools.put(func("create_dir",
                    "创建目录（含所有中间目录）。已存在则视为成功。",
                    pMkdir, new JSONArray().put("path")));

            JSONObject pMove = new JSONObject();
            pMove.put("src", prop("string", "源文件/目录路径"));
            pMove.put("dst", prop("string", "目标路径"));
            pMove.put("overwrite", propBool("目标已存在时是否覆盖（默认 false）"));
            tools.put(func("move_file",
                    "移动文件或目录到另一个位置（跨目录）。如果只是改名字不换目录，用 rename_file 更合适。",
                    pMove, new JSONArray().put("src").put("dst")));

            JSONObject pRename = new JSONObject();
            pRename.put("path", prop("string", "要重命名的文件或目录的完整路径（含原文件名）"));
            pRename.put("new_name", prop("string", "新的文件名（只填文件名，不要带路径分隔符）"));
            tools.put(func("rename_file",
                    "重命名文件或目录（只改名字，不移动位置）。如果要移动到其他目录，请用 move_file。",
                    pRename, new JSONArray().put("path").put("new_name")));

            JSONObject pCopy = new JSONObject();
            pCopy.put("src", prop("string", "源文件/目录路径"));
            pCopy.put("dst", prop("string", "目标路径"));
            pCopy.put("overwrite", propBool("目标已存在时是否覆盖（默认 false）"));
            tools.put(func("copy_file",
                    "复制文件或目录（目录会递归复制）。",
                    pCopy, new JSONArray().put("src").put("dst")));

            JSONObject pDel = new JSONObject();
            pDel.put("path", prop("string", "要删除的文件或目录路径"));
            pDel.put("recursive", propBool("删除目录时是否连同内容一起删（默认 false）"));
            tools.put(func("delete_file",
                    "删除文件或目录。删除目录必须把 recursive 设为 true。删除前系统会向用户确认（用户可在设置里关闭确认）。",
                    pDel, new JSONArray().put("path")));

            JSONObject pSearch = new JSONObject();
            pSearch.put("path", prop("string", "搜索的起始目录路径"));
            pSearch.put("keyword", prop("string", "搜索关键词，不区分大小写"));
            pSearch.put("in_content", propBool("true = 搜索文件内容（grep 式，返回文件名与行号）；false = 只匹配文件名"));
            tools.put(func("search_files",
                    "在目录中搜索文件。按文件名找文件用 in_content=false；想知道哪个文件里提到过某句话用 in_content=true。",
                    pSearch, new JSONArray().put("path").put("keyword")));

            // ---------- 时间 ----------

            JSONObject pTime = new JSONObject();
            tools.put(func("get_current_time",
                    "获取当前日期和时间（含星期）。写周报、按日期归档、计算时间间隔前先调用它。",
                    pTime, null));

            // ---------- 与用户交互 ----------

            JSONObject pAsk = new JSONObject();
            pAsk.put("question", prop("string", "要向用户提的问题"));
            pAsk.put("options", prop("string", "可选：逗号分隔的选项列表（用户可从中选一个，也可自行输入）"));
            tools.put(func("ask_user",
                    "向用户提问，等待用户回答后再继续。当信息不足、方案有多种选择、或即将执行危险操作前，都应该先调用它，而不是自己猜测。",
                    pAsk, new JSONArray().put("question")));

            // ---------- 长期记忆 ----------

            JSONObject pMemSave = new JSONObject();
            pMemSave.put("key", prop("string", "记忆的标识（英文或数字，如 user_pref / project_ai）"));
            pMemSave.put("content", prop("string", "要记住的内容"));
            tools.put(func("save_memory",
                    "把一条信息写入长期记忆（跨会话保存）。当用户告诉你他的偏好、常用路径、项目背景时，主动保存下来。",
                    pMemSave, new JSONArray().put("key").put("content")));

            JSONObject pMemRead = new JSONObject();
            pMemRead.put("key", prop("string", "要读取的记忆标识"));
            tools.put(func("read_memory",
                    "读取指定长期记忆。",
                    pMemRead, new JSONArray().put("key")));

            JSONObject pMemList = new JSONObject();
            tools.put(func("list_memory",
                    "列出所有长期记忆的标识与摘要。",
                    pMemList, null));

            // ---------- 联网 ----------

            JSONObject pSearchWeb = new JSONObject();
            pSearchWeb.put("query", prop("string", "搜索关键词"));
            tools.put(func("web_search",
                    "联网搜索。需要实时信息、最新消息、或你自己不知道的内容时使用，返回若干条结果的标题、链接和摘要。",
                    pSearchWeb, new JSONArray().put("query")));

            JSONObject pFetch = new JSONObject();
            pFetch.put("url", prop("string", "要抓取的完整网址，以 http:// 或 https:// 开头"));
            tools.put(func("fetch_url",
                    "抓取指定网页并返回其纯文本内容。通常先用 web_search 拿到链接，再用本工具阅读具体页面。",
                    pFetch, new JSONArray().put("url")));

            // ---------- 本机 shell（默认关闭，需用户在设置里开启） ----------

            if (allowShellTool) {
                JSONObject pShell = new JSONObject();
                pShell.put("command", prop("string", "要执行的 shell 命令（sh 语法）。工作目录固定 /sdcard。执行 pip/python 前请先 which python3 python pip node perl 探测可用解释器。"));
                pShell.put("timeout_sec", propInt("超时秒数（默认 15，最大 60）"));
                tools.put(func("run_shell_command",
                        "在手机本机执行 shell 命令并返回 stdout/stderr/退出码。适合 ls、cat、df、ps、top -n 1、getprop、dumpsys、find、grep、pm list packages 等系统命令。"
                      + "以普通应用权限运行（无 root）。注意：Android 原生 shell 没有 Python，pip 不可用；"
                      + "需要脚本/调用库时改用 run_js 工具，或引导用户安装 Termux。禁止用于破坏系统或侵犯隐私。",
                        pShell, new JSONArray().put("command")));

                JSONObject pJs = new JSONObject();
                pJs.put("code", prop("string", "要执行的 JavaScript 代码。代码中调用 __send(结果) 返回结果（对象会被 JSON 序列化）；同步执行完 2.5 秒仍无返回会自动结束。可用同步 XMLHttpRequest 拉取远程 JS 库后 eval（基址 file:/// 已放开跨域），例如: var x=new XMLHttpRequest();x.open('GET','https://cdn.jsdelivr.net/npm/lodash@4/lodash.min.js',false);x.send(null);eval(x.responseText);"));
                pJs.put("timeout_sec", propInt("超时秒数（默认 15，最大 60）"));
                tools.put(func("run_js",
                        "在手机内置的 JS 引擎（WebView V8）中执行 JavaScript。适合：数据计算、JSON/文本处理、加载并调用 JS 库（先 XHR 拉库再 eval）、调用网页 API。"
                      + "没有 Python 环境，不要用它跑 Python 代码。",
                        pJs, new JSONArray().put("code")));
            }

            // ---------- 无障碍操控手机（默认关闭，需用户开启） ----------

            if (allowA11yTool) {
                JSONObject pA11y = new JSONObject();
                pA11y.put("action", prop("string", "screen=读取当前屏幕上可见控件的文本与坐标；screenshot=截屏并自动附到对话（视觉识别，微信等读不到控件时用它）；tap=点击指定坐标(x,y)；long_press=长按(x,y)；swipe=滑动(x1,y1→x2,y2,duration_ms)；click_text=点击包含指定文字的控件；input=向当前输入框写入文本(text)。input 若读不到输入框，先 tap 输入框聚焦再重试；写入失败会自动降级为剪贴板粘贴，仍失败时文本会留在剪贴板；back=返回键；home=回到桌面；scroll=滚动屏幕(direction=up/down)；launch_app=启动应用(package=包名 或 app_name=名称关键词)；sleep=等待界面加载(ms)；recents=最近任务；notifications=通知栏；windows=列出窗口诊断；stop_projection=停止屏幕共享（关闭投屏与状态栏通知，视觉任务完成后调用）"));
                pA11y.put("x", propInt("tap/long_press 用：横坐标 px"));
                pA11y.put("y", propInt("tap/long_press 用：纵坐标 px"));
                pA11y.put("x1", propInt("swipe 用：起点横坐标"));
                pA11y.put("y1", propInt("swipe 用：起点纵坐标"));
                pA11y.put("x2", propInt("swipe 用：终点横坐标"));
                pA11y.put("y2", propInt("swipe 用：终点纵坐标"));
                pA11y.put("duration_ms", propInt("swipe 用：滑动时长（默认 400ms，100~3000）"));
                pA11y.put("from_vision", propBool("坐标来自截图分析时设为 true。x/y 必须用截图上的像素坐标（截图叠加了 10% 网格与像素刻度，请按刻度读坐标，不要凭比例目测），App 会自动换算成真实屏幕坐标；若最近没有成功截过图会返回错误"));
                pA11y.put("text", prop("string", "click_text 用：要点击的控件文字；input 用：要输入的文本"));
                pA11y.put("direction", prop("string", "scroll 用：up 或 down"));
                pA11y.put("package", prop("string", "launch_app 用：应用包名，如 com.tencent.mm"));
                pA11y.put("app_name", prop("string", "launch_app 用：应用名称关键词，如 微信"));
                pA11y.put("ms", propInt("sleep 用：等待毫秒数（最大 5000）"));
                tools.put(func("accessibility_control",
                        "通过无障碍服务操控手机，可帮用户自动化操作其他 App。标准流程："
                      + "① launch_app 启动目标应用（如 package=com.tencent.mm 启动微信）；"
                      + "② action=sleep 等 500~1000ms 让界面加载；"
                      + "③ action=screen 读取屏幕上的控件文本与坐标；"
                      + "④ click_text 或 tap 精确点击；input 输入文字；scroll 滚动查找。"
                      + "★ 重要：如果 screen 读不到任何控件（微信等 App 限制无障碍读取），改走视觉方案："
                      + "调用 action=screenshot，截图会自动附到对话里（图上叠加了 10% 网格线与像素刻度），你看到图后分析要操作的元素，"
                      + "返回 tap/long_press/swipe 并把 from_vision 设为 true（坐标直接用截图上的像素坐标，尽量按图上刻度读，不要凭比例目测，App 自动换算）。"
                      + "首次 screenshot 会触发系统截屏授权弹窗，请提示用户点「立即开始」后重试。"
                      + "★ 视觉操控任务全部完成后（不再需要截屏/点击），必须主动调用 action=stop_projection 停止屏幕共享，"
                      + "释放投屏并清除状态栏「屏幕共享中」通知，保护用户隐私。"
                      + "input 输入文字：若返回读不到节点，先 tap 输入框聚焦、sleep 800ms 后再 input；"
                      + "若 SET_TEXT 与剪贴板粘贴都失败，文本已自动复制到剪贴板，引导用户长按输入框粘贴。"
                      + "每次操作后如果界面可能变化，建议 sleep 后用 screen 或 screenshot 确认结果。"
                      + "仅在用户已开启系统无障碍服务后可用。涉及支付、密码、删除等敏感操作时必须先征得用户同意。",
                pA11y, new JSONArray().put("action")));
    }

    // ---------- 文生图 ----------

    if (allowImageGen) {
        JSONObject pImg = new JSONObject();
        pImg.put("prompt", prop("string", "图像描述，越具体越好（中英文均可）。建议包含：主体 + 场景 + 风格 + 色调 + 构图"));
        pImg.put("size", prop("string", "图片尺寸，如 1024x1024。留空使用设置里的默认尺寸"));
        tools.put(func("generate_image",
                "根据文字描述生成图片。生成的图片会自动附到对话里，你也能在下一轮看到它。"
              + "适合：插画、封面、示意图、概念图、表情包、海报等。"
              + "prompt 越具体效果越好；一次只生成一张图。若失败会返回错误原因，不要连续重试相同 prompt。",
                pImg, new JSONArray().put("prompt")));
    }

} catch (Throwable e) {
    // 工具定义失败不影响主流程
}
return tools;
    }
}