package com.ai.office;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * 视觉操控的截屏内核：MediaProjection + ImageReader，零依赖（系统 API 21+）。
 *
 * - 会话由 ProjectionService（前台服务 type=mediaProjection）持有：Android 14+ 强制要求
 * - 授权流程：系统弹窗点「立即开始」→ 启动 ProjectionService → startWith() 建立会话
 * - capture() 返回当前屏幕 Bitmap；记录最近一帧尺寸用于坐标换算
 * - 停止条件：用户从状态栏停止投屏 / 调用 stop()（会联动停止前台服务）
 */
public class ProjectionController {

    private static MediaProjection sProjection;
    private static VirtualDisplay sDisplay;
    private static ImageReader sReader;
    private static Service sOwnerService;
    private static int sScreenWidth, sScreenHeight;
    private static volatile int sLastImgW, sLastImgH;
    // 发给 AI 的截图（可能已缩放到 720 宽）的实际尺寸；from_vision 坐标换算用这个，不能用投影原始分辨率
    private static volatile int sVisionImgW, sVisionImgH;
    // 空闲自动停止：连续 10 分钟没有截屏请求就自动关闭投屏（防止 AI 忘记 stop_projection、通知常驻耗电）
    private static Handler sMainHandler;
    private static final long IDLE_STOP_MS = 10L * 60L * 1000L;
    private static final Runnable sIdleStopper = new Runnable() {
        @Override public void run() { stop(); }
    };

    private static void scheduleIdleStop() {
        try {
            if (sMainHandler != null) {
                sMainHandler.removeCallbacks(sIdleStopper);
                sMainHandler.postDelayed(sIdleStopper, IDLE_STOP_MS);
            }
        } catch (Throwable t) {}
    }

    public static boolean isReady() { return sReader != null; }

    public static int screenWidth() { return sScreenWidth; }

    public static int screenHeight() { return sScreenHeight; }

    public static int lastImageWidth() { return sLastImgW; }

    public static int lastImageHeight() { return sLastImgH; }

    public static void setVisionImageSize(int w, int h) { sVisionImgW = w; sVisionImgH = h; }

    public static int visionImageWidth() { return sVisionImgW; }

    public static int visionImageHeight() { return sVisionImgH; }

    /** 在 ProjectionService 的 startForeground 之后调用（Android 14 强制顺序） */
    public static synchronized void startWith(Context ctx, int resultCode, Intent data) {
        try {
            stop();
            if (ctx instanceof Service) sOwnerService = (Service) ctx;

            MediaProjectionManager mpm =
                    (MediaProjectionManager) ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            MediaProjection mp = mpm.getMediaProjection(resultCode, data);
            if (mp == null) { stop(); return; }
            sProjection = mp;

            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            sScreenWidth = dm.widthPixels;
            sScreenHeight = dm.heightPixels;

            sReader = ImageReader.newInstance(sScreenWidth, sScreenHeight, PixelFormat.RGBA_8888, 2);
            Handler h = new Handler(Looper.getMainLooper());
            sMainHandler = h;
            scheduleIdleStop();
            sDisplay = mp.createVirtualDisplay("aio_vision",
                    sScreenWidth, sScreenHeight, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    sReader.getSurface(), null, h);
            mp.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stop(); }
            }, h);

            try { Thread.sleep(250L); } catch (Throwable t) {}   // 等首帧
            sLastImgW = sScreenWidth;
            sLastImgH = sScreenHeight;
        } catch (Throwable t) {
            stop();
        }
    }

    /** 抓取当前屏幕帧；失败返回 null */
    public static synchronized Bitmap capture() {
        try {
            if (sReader == null) return null;
            Image img = sReader.acquireLatestImage();
            if (img == null) return null;
            scheduleIdleStop();   // 有活动就重置空闲计时
            int w = img.getWidth();
            int h = img.getHeight();
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buf = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            sLastImgW = w;
            sLastImgH = h;

            Bitmap raw = Bitmap.createBitmap(rowStride / pixelStride, h, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(buf);
            img.close();

            if (raw.getWidth() > w) {   // rowStride 对齐产生的多余列，裁掉
                raw = Bitmap.createBitmap(raw, 0, 0, w, h);
            }
            return raw;
        } catch (Throwable t) {
            return null;
        }
    }

    public static synchronized void stop() {
        try { if (sMainHandler != null) sMainHandler.removeCallbacks(sIdleStopper); } catch (Throwable t) {}
        try { if (sReader != null) sReader.close(); } catch (Throwable t) {}
        try { if (sDisplay != null) sDisplay.release(); } catch (Throwable t) {}
        try { if (sProjection != null) sProjection.stop(); } catch (Throwable t) {}
        sReader = null;
        sDisplay = null;
        sProjection = null;
        sVisionImgW = 0;
        sVisionImgH = 0;
        Service owner = sOwnerService;
        sOwnerService = null;
        if (owner != null) {
            try { owner.stopSelf(); } catch (Throwable t) {}
        }
    }
}
