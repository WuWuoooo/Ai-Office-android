package com.ai.office;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 视觉操控的投屏前台服务。
 *
 * Android 14 起系统强制：MediaProjection 必须在 type=mediaProjection 的前台服务中使用，
 * 否则 getMediaProjection() 直接抛 SecurityException（表现为「启动失败」）。
 * 因此授权弹窗点「立即开始」后，先启动本服务 startForeground，再建立投屏会话。
 */
public class ProjectionService extends Service {

    private static final String CHANNEL_ID = "aio_projection";
    private static final int NOTIFY_ID = 2001;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForegroundCompat();
            int rc = intent == null ? -1 : intent.getIntExtra("rc", -1);
            Intent data = intent == null ? null : (Intent) intent.getParcelableExtra("data");
            ProjectionController.startWith(this, rc, data);
        } catch (Throwable t) {
            try { stopSelf(); } catch (Throwable tt) {}
        }
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "屏幕识别",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("AI 视觉识别期间保持屏幕投影会话");
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
            else b = new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_menu_view)
             .setContentTitle("AI Office 视觉识别中")
             .setContentText("正在保持屏幕投影会话（截屏识别用），可在状态栏停止")
             .setOngoing(true);
            Notification n = b.build();
            if (Build.VERSION.SDK_INT >= 29) {
                // API 29+ 显式声明前台服务类型，Android 14 强制校验
                startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFY_ID, n);
            }
        } catch (Throwable t) {
            try { startForeground(NOTIFY_ID, new Notification()); } catch (Throwable tt) {}
        }
    }
}
