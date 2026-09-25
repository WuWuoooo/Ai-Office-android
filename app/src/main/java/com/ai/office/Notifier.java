package com.ai.office;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 后台生成完成提醒（原生 Notification，无 AndroidX）
 */
public class Notifier {

    private static final String CHANNEL_ID = "ai_office_done";
    private static final int NOTIFY_ID = 1001;

    public static void notifyDone(Context ctx, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "生成完成", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("AI 回复生成完成时的提醒");
                nm.createNotificationChannel(ch);
            }

            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                                   : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, flags);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(ctx, CHANNEL_ID);
            else b = new Notification.Builder(ctx);

            b.setSmallIcon(android.R.drawable.stat_notify_chat)
             .setContentTitle(title == null ? "AI Office" : title)
             .setContentText(text == null ? "生成完成" : text)
             .setAutoCancel(true)
             .setContentIntent(pi);

            if (Build.VERSION.SDK_INT < 26) {
                b.setPriority(Notification.PRIORITY_DEFAULT);
            }
            nm.notify(NOTIFY_ID, b.build());
        } catch (Throwable t) {
            // 通知失败不影响主流程
        }
    }

    public static void cancel(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFY_ID);
        } catch (Throwable t) {}
    }
}
