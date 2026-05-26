package com.sunnynet.tools.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.sunnynet.tools.capture.CaptureEngine;

/**
 * 抓包前台服务：保证通知栏常驻并显示「停止抓包」操作按钮。
 */
public class CaptureForegroundService extends Service {

    public static final String ACTION_START = "com.sunnynet.tools.action.CAPTURE_FG_START";
    public static final String ACTION_STOP = "com.sunnynet.tools.action.CAPTURE_FG_STOP";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopCaptureAndSelf();
            return START_NOT_STICKY;
        }
        android.app.Notification notification = CaptureNotificationHelper.buildNotification(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    CaptureNotificationHelper.getNotificationId(),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(CaptureNotificationHelper.getNotificationId(), notification);
        } else {
            startForeground(CaptureNotificationHelper.getNotificationId(), notification);
        }
        return START_STICKY;
    }

    private void stopCaptureAndSelf() {
        new Thread(() -> {
            CaptureEngine.get().stop();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        }, "capture-fg-stop").start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
