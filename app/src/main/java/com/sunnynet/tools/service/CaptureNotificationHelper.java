package com.sunnynet.tools.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.sunnynet.tools.MainActivity;
import com.sunnynet.tools.R;

/**
 * 抓包进行中的系统通知，提供不依赖界面的「停止」入口。
 */
public final class CaptureNotificationHelper {

    /** v2：提高优先级，确保通知栏显示「停止」按钮 */
    private static final String CHANNEL_ID = "sunnynet_capture_v2";
    private static final int NOTIFICATION_ID = 1001;

    private CaptureNotificationHelper() {
    }

    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    public static void showCapturing(Context context) {
        Context app = context.getApplicationContext();
        if (!canPostNotifications(app)) {
            return;
        }
        ensureChannel(app);
        Intent serviceIntent = new Intent(app, CaptureForegroundService.class);
        serviceIntent.setAction(CaptureForegroundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(serviceIntent);
        } else {
            app.startService(serviceIntent);
        }
    }

    public static void dismiss(Context context) {
        Context app = context.getApplicationContext();
        Intent stopService = new Intent(app, CaptureForegroundService.class);
        app.stopService(stopService);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    /** Android 13+ 需已授予 POST_NOTIFICATIONS */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_capture),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.notification_channel_capture_desc));
        channel.setShowBadge(false);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    public static Notification buildNotification(Context context) {
        ensureChannel(context);

        Intent contentIntent = new Intent(context, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int contentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentPending = PendingIntent.getActivity(
                context, 0, contentIntent, contentFlags);

        Intent stopIntent = new Intent(context, CaptureForegroundService.class);
        stopIntent.setAction(CaptureForegroundService.ACTION_STOP);
        int stopFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopPending = PendingIntent.getService(context, 1, stopIntent, stopFlags);

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_stop),
                context.getString(R.string.notification_stop_capture),
                stopPending
        ).build();

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_capture_title))
                .setContentText(context.getString(R.string.notification_capture_text))
                .setContentIntent(contentPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(stopAction)
                .build();
    }
}
