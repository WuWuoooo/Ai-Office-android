package com.ai.office;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 文生图客户端（同步）。
 *
 * 协议：POST {baseUrl}/images/generations
 *   body: {"model":"...","prompt":"...","size":"1024x1024","n":1}
 *   返回: {"data":[{"url":"..."}]} 或 {"data":[{"b64_json":"..."}]}
 *   URL 会自动下载并转成 JPEG base64。
 *
 * 返回：
 *   成功 → "@@IMAGE@@<base64>"
 *   失败 → "错误: ..."
 */
public class ImageGenClient {

    public static final String IMAGE_PREFIX = "@@IMAGE@@";

    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int TIMEOUT_CONNECT_MS = 20000;
    private static final int TIMEOUT_READ_MS = 120000;

    public static String generateSync(Context ctx, String argsJson) {
        try {
            String prompt = "";
            String size = "";
            try {
                JSONObject a = new JSONObject(argsJson == null || argsJson.trim().length() == 0 ? "{}" : argsJson);
                prompt = a.optString("prompt", "").trim();
                size = a.optString("size", "").trim();
            } catch (Throwable t) {}

            if (prompt.length() == 0) return "错误: prompt 不能为空";

            String[] cfg = ImageGenConfig.resolve(ctx);
            String base = cfg[0];
            String key = cfg[1];
            String model = cfg[2];
            String defSize = cfg[3];

            if (base.length() == 0) return "错误: 未配置文生图 API 地址（请在 设置→文生图 中配置）";
            if (key.length() == 0) return "错误: 未配置文生图 API Key（可在 设置→文生图 或 设置→AI 接入 中配置）";
            if (model.length() == 0) return "错误: 未配置文生图模型";
            if (size.length() == 0) size = defSize;

            String url = base;
            if (!url.endsWith("/")) url = url + "/";
            url = url + "images/generations";

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("size", size);
            body.put("n", 1);

            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
                conn.setReadTimeout(TIMEOUT_READ_MS);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String resp = readAllText(is, 2 * 1024 * 1024);
                if (code < 200 || code >= 300) {
                    String err = resp == null ? "" : resp;
                    if (err.length() > 400) err = err.substring(0, 400) + "…";
                    return "错误: 生成失败 HTTP " + code + ": " + err;
                }
                if (resp == null || resp.trim().length() == 0) return "错误: 返回为空";

                JSONObject root = new JSONObject(resp);
                JSONArray data = root.optJSONArray("data");
                if (data == null || data.length() == 0) {
                    String err = root.optString("error", "");
                    if (err.length() > 0) return "错误: " + err;
                    return "错误: 返回中没有图片数据";
                }
                JSONObject item = data.optJSONObject(0);
                if (item == null) return "错误: 图片数据格式异常";

                String b64 = item.optString("b64_json", "");
                if (b64 != null && b64.length() > 0) {
                    return IMAGE_PREFIX + b64;
                }

                String imgUrl = item.optString("url", "");
                if (imgUrl != null && imgUrl.length() > 0) {
                    byte[] raw = downloadBytes(imgUrl);
                    if (raw == null || raw.length == 0) return "错误: 图片下载失败";
                    String jb64 = convertToJpegBase64(raw);
                    if (jb64 == null) return "错误: 图片转换失败";
                    return IMAGE_PREFIX + jb64;
                }

                return "错误: 返回中没有 url 或 b64_json";
            } finally {
                if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
            }
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        }
    }

    private static byte[] downloadBytes(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
            conn.setReadTimeout(TIMEOUT_READ_MS);
            conn.setRequestProperty("User-Agent", "AI-Office-Android");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            return readAllBytes(conn.getInputStream(), MAX_DOWNLOAD_BYTES);
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable t) {}
        }
    }

    private static String convertToJpegBase64(byte[] raw) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            int sample = 1;
            while (o.outWidth > 0 && (o.outWidth / sample) > 1536) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, o2);
            if (bm == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 90, bos);
            try { bm.recycle(); } catch (Throwable t) {}
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readAllText(InputStream is, int maxBytes) {
        byte[] b = readAllBytes(is, maxBytes);
        if (b == null) return null;
        try { return new String(b, "UTF-8"); } catch (Throwable t) { return null; }
    }

    private static byte[] readAllBytes(InputStream is, int maxBytes) {
        if (is == null) return null;
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
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}