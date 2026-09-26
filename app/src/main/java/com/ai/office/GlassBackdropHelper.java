package com.ai.office;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * 毛玻璃内核（实验性）：把一张位图做高斯近似模糊。
 *
 * 实现思路（StackBlur 变种）：
 *   1. 缩小到 1/DOWNSCALE → 2. 3 次水平 + 3 次垂直 box blur → 3. 放大回原尺寸
 * 视觉上接近高斯模糊，速度比逐像素卷积快 10 倍以上，且完全零依赖，API 19+ 可用。
 */
public class GlassBackdropHelper {

    private static final int DOWNSCALE = 6;
    private static final int DEFAULT_RADIUS = 20;

    /** 从文件路径读取并模糊；失败返回 null */
    public static Bitmap blurFromFile(String path, int maxPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while ((o.outWidth / sample) > maxPx * 2 || (o.outHeight / sample) > maxPx * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(path, o2);
            if (src == null) return null;
            Bitmap blurred = fastBlur(src, DEFAULT_RADIUS);
            if (blurred != src) try { src.recycle(); } catch (Throwable t) {}
            return blurred;
        } catch (Throwable t) { return null; }
    }

    /** 对位图做模糊；失败返回原图 */
    public static Bitmap fastBlur(Bitmap src, int radius) {
        if (src == null || radius <= 0) return src;
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            if (w < 4 || h < 4) return src;

            int sw = Math.max(1, w / DOWNSCALE);
            int sh = Math.max(1, h / DOWNSCALE);
            Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);

            int[] pixels = new int[sw * sh];
            small.getPixels(pixels, 0, sw, 0, 0, sw, sh);
            if (small != src) try { small.recycle(); } catch (Throwable t) {}

            int r = Math.max(1, radius / DOWNSCALE);
            for (int pass = 0; pass < 3; pass++) {
                boxBlurHorizontal(pixels, sw, sh, r);
                boxBlurVertical(pixels, sw, sh, r);
            }

            Bitmap smallOut = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            smallOut.setPixels(pixels, 0, sw, 0, 0, sw, sh);

            Bitmap out = Bitmap.createScaledBitmap(smallOut, w, h, true);
            if (out != smallOut) try { smallOut.recycle(); } catch (Throwable t) {}
            return out;
        } catch (Throwable t) { return src; }
    }

    private static void boxBlurHorizontal(int[] pixels, int w, int h, int r) {
        if (r <= 0) return;
        int[] line = new int[w];
        int win = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) line[x] = pixels[base + x];

            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            for (int i = -r; i <= r; i++) {
                int c = line[clamp(i, 0, w - 1)];
                sumA += (c >>> 24) & 0xFF;
                sumR += (c >> 16) & 0xFF;
                sumG += (c >> 8) & 0xFF;
                sumB += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                int a = sumA / win, rr = sumR / win, gg = sumG / win, bb = sumB / win;
                pixels[base + x] = (a << 24) | (rr << 16) | (gg << 8) | bb;

                int outC = line[clamp(x - r, 0, w - 1)];
                int inC  = line[clamp(x + r + 1, 0, w - 1)];
                sumA += ((inC >>> 24) & 0xFF) - ((outC >>> 24) & 0xFF);
                sumR += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                sumG += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                sumB += (inC & 0xFF) - (outC & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] pixels, int w, int h, int r) {
        if (r <= 0) return;
        int[] line = new int[h];
        int win = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) line[y] = pixels[y * w + x];

            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            for (int i = -r; i <= r; i++) {
                int c = line[clamp(i, 0, h - 1)];
                sumA += (c >>> 24) & 0xFF;
                sumR += (c >> 16) & 0xFF;
                sumG += (c >> 8) & 0xFF;
                sumB += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                int a = sumA / win, rr = sumR / win, gg = sumG / win, bb = sumB / win;
                pixels[y * w + x] = (a << 24) | (rr << 16) | (gg << 8) | bb;

                int outC = line[clamp(y - r, 0, h - 1)];
                int inC  = line[clamp(y + r + 1, 0, h - 1)];
                sumA += ((inC >>> 24) & 0xFF) - ((outC >>> 24) & 0xFF);
                sumR += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                sumG += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                sumB += (inC & 0xFF) - (outC & 0xFF);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}