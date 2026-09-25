package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * TTS 朗读助手：
 *   - 优先调用用户在设置里配置的 TTS API（如智谱 glm-tts），返回 wav/mp3 后本地播放
 *   - 未配置 API URL 时回退到系统 TextToSpeech
 *
 * 使用：
 *   TtsHelper.speak(ctx, text, callback);
 *   TtsHelper.stop();
 *
 * 配置项（SharedPreferences：ai_office_config）：
 *   tts_enabled  boolean 是否启用（默认 false）
 *   tts_api_url  string  TTS API 地址（为空则用系统 TTS）
 *   tts_api_key  string  Bearer Key
 *   tts_model    string  模型名（默认 glm-tts）
 *   tts_voice    string  音色（默认 tongtong）
 *   tts_format   string  返回格式（默认 wav）
 *   tts_speed    string  语速（可选，留空不发送）
 */
public class TtsHelper {

    private static MediaPlayer sPlayer;
    private static TextToSpeech sSysTts;
    private static volatile boolean sSysReady = false;
    private static File sLastFile;

    public interface TtsCallback {
        void onStart();
        void onSuccess();
        void onError(String message);
    }

    public static boolean isEnabled(Context ctx) {
        try {
            return UiUtils.getBool(ctx, "tts_enabled", false);
        } catch (Throwable t) { return false; }
    }

    public static synchronized boolean isSpeaking() {
        try {
            if (sPlayer != null && sPlayer.isPlaying()) return true;
        } catch (Throwable t) {}
        try {
            if (sSysTts != null && sSysTts.isSpeaking()) return true;
        } catch (Throwable t) {}
        return false;
    }

    public static synchronized void stop() {
        try {
            if (sPlayer != null) {
                sPlayer.stop();
                sPlayer.release();
                sPlayer = null;
            }
        } catch (Throwable t) {}
        try {
            if (sSysTts != null) sSysTts.stop();
        } catch (Throwable t) {}
    }

    public static void speak(final Context ctx, final String text, final TtsCallback cb) {
        if (ctx == null) {
            if (cb != null) cb.onError("Context 为空");
            return;
        }
        if (text == null || text.trim().length() == 0) {
            if (cb != null) cb.onError("文本为空");
            return;
        }
        if (!isEnabled(ctx)) {
            if (cb != null) cb.onError("TTS 未启用");
            return;
        }
        // 过长文本只读前 2000 字（防止 API 侧超限，也避免用户等太久）
        final String t = text.length() > 2000 ? text.substring(0, 2000) : text;

        stop();

        final String apiUrl = UiUtils.getStr(ctx, "tts_api_url", "");
        if (apiUrl == null || apiUrl.trim().length() == 0) {
            speakSystem(ctx, t, cb);
            return;
        }

        final Context appCtx = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] audio = requestTts(appCtx, apiUrl.trim(), t);
                    if (audio == null || audio.length == 0) {
                        if (cb != null) cb.onError("TTS 返回空音频");
                        return;
                    }
                    playAudio(appCtx, audio, cb);
                } catch (Throwable e) {
                    if (cb != null) cb.onError(e.getMessage() == null ? "TTS 请求失败" : e.getMessage());
                }
            }
        }).start();
    }

    // ============================================================
    // API TTS
    // ============================================================

    private static byte[] requestTts(Context ctx, String apiUrl, String text) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
        String key = p.getString("tts_api_key", "");
        if (key == null) key = "";
        key = key.trim();

        String model = p.getString("tts_model", "glm-tts");
        if (model == null || model.trim().length() == 0) model = "glm-tts";

        String voice = p.getString("tts_voice", "tongtong");
        if (voice == null || voice.trim().length() == 0) voice = "tongtong";

        String fmt = p.getString("tts_format", "wav");
        if (fmt == null || fmt.trim().length() == 0) fmt = "wav";

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (key.length() > 0) conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("input", text);
            body.put("voice", voice);
            body.put("response_format", fmt);

            // 可选：语速
            try {
                String speed = p.getString("tts_speed", "");
                if (speed != null && speed.trim().length() > 0) {
                    body.put("speed", Float.parseFloat(speed.trim()));
                }
            } catch (Throwable t) {}

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readAllText(conn.getErrorStream());
                if (err.length() > 300) err = err.substring(0, 300) + "…";
                throw new Exception("TTS 请求失败 " + code + ": " + err);
            }
            return readAllBytes(conn.getInputStream());
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private static void playAudio(final Context ctx, byte[] data, final TtsCallback cb) {
        try {
            File f = new File(ctx.getCacheDir(), "tts_" + System.currentTimeMillis() + ".wav");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();

            // 清理上一次的临时文件
            if (sLastFile != null && sLastFile.exists() && !sLastFile.equals(f)) {
                try { sLastFile.delete(); } catch (Throwable t) {}
            }
            sLastFile = f;

            final File ff = f;
            final MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    try {
                        m.start();
                        if (cb != null) cb.onStart();
                    } catch (Throwable t) {
                        if (cb != null) cb.onError(t.getMessage());
                    }
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    try { m.release(); } catch (Throwable t) {}
                    if (sPlayer == mp) sPlayer = null;
                    if (cb != null) cb.onSuccess();
                    try { ff.delete(); } catch (Throwable t) {}
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    try { m.release(); } catch (Throwable t) {}
                    if (sPlayer == mp) sPlayer = null;
                    if (cb != null) cb.onError("播放失败 what=" + what + " extra=" + extra);
                    try { ff.delete(); } catch (Throwable t) {}
                    return true;
                }
            });
            sPlayer = mp;
            mp.prepareAsync();
        } catch (Throwable t) {
            if (cb != null) cb.onError(t.getMessage() == null ? "播放失败" : t.getMessage());
        }
    }

    // ============================================================
    // 系统 TTS 回退
    // ============================================================

    private static void speakSystem(final Context ctx, final String text, final TtsCallback cb) {
        try {
            if (sSysTts == null) {
                sSysReady = false;
                sSysTts = new TextToSpeech(ctx.getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        sSysReady = (status == TextToSpeech.SUCCESS);
                    }
                });
            }
            // 等待初始化，最多 3 秒
            final long start = System.currentTimeMillis();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!sSysReady && System.currentTimeMillis() - start < 3000) {
                            try { Thread.sleep(100); } catch (Throwable t) {}
                        }
                        if (sSysReady && sSysTts != null) {
                            try { sSysTts.setLanguage(Locale.CHINESE); } catch (Throwable t) {}
                            if (cb != null) cb.onStart();
                            sSysTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aio_tts");
                            if (cb != null) cb.onSuccess();
                        } else {
                            if (cb != null) cb.onError("系统 TTS 未就绪");
                        }
                    } catch (Throwable t) {
                        if (cb != null) cb.onError(t.getMessage() == null ? "系统 TTS 失败" : t.getMessage());
                    }
                }
            }).start();
        } catch (Throwable t) {
            if (cb != null) cb.onError(t.getMessage() == null ? "系统 TTS 初始化失败" : t.getMessage());
        }
    }

    // ============================================================
    // 流读取工具
    // ============================================================

    private static byte[] readAllBytes(InputStream is) {
        if (is == null) return new byte[0];
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return bos.toByteArray();
        } catch (Throwable t) { return new byte[0]; }
    }

    private static String readAllText(InputStream is) {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) { return ""; }
    }
}